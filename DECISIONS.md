# DECISIONS.md — 기술적 판단과 트레이드오프

---

## 쟁점 1. 선착순 재고 처리 — Redis Lua Script vs. DB 비관적 락

### 상황

평시 50TPS이지만 프로모션이 시작되는 00시에 1~5분간 순간적으로 500~1000TPS가 예상됩니다.
재고는 10개뿐이고 수천 명이 동시에 예약을 시도하므로, 두 가지를 동시에 보장해야 했습니다.

- **정합성**: 재고가 10개를 초과하거나 음수가 되는 초과판매가 절대 발생하지 않아야 합니다.
- **공정성**: 먼저 요청한 사용자가 먼저 재고를 가져가야 합니다. 느린 사용자가 운 좋게 성공하거나, 빠른 사용자가 탈락하는 일이 없어야 합니다.

### 선택지

| 방식 | 정합성 | 공정성 | 1000TPS 처리 |
|------|--------|--------|--------------|
| DB 비관적 락 (SELECT FOR UPDATE) | ✅ 보장 | ✅ 락 대기 순서 | ❌ Connection Pool 고갈 |
| DB 낙관적 락 (version) | ⚠️ 재시도 필요 | ❌ 재시도 순서 불확정 | ❌ 재시도 폭풍 |
| Redis Lua Script | ✅ 원자적 보장 | ✅ 도달 순서 = 처리 순서 | ✅ 마이크로초 응답 |

### 왜 그렇게 판단했는지

**DB 비관적 락**은 정합성은 완벽하지만, 1000개의 트랜잭션이 동일 행에서 줄 서면 락 대기 시간이 누적되고 Connection Pool이 고갈됩니다. 실제로 300 VU 테스트에서 MySQL 데드락(Error 1213)이 발생해 500 에러가 발생했습니다.

**DB 낙관적 락**은 고경쟁 환경에서 대부분이 충돌 후 재시도하며 재시도 폭풍이 발생합니다. 재시도 순서가 보장되지 않아 공정성도 깨집니다.

**Redis Lua Script**는 Redis 싱글 스레드 위에서 Lua Script가 원자적으로 실행됩니다. 아래 5단계를 하나의 스크립트로 묶어 Race Condition 없이 처리합니다.

```
1. sale_start:promotion:{id} vs Redis TIME → 판매 시작 전이면 NOT_STARTED
2. rate_limit:{userId} INCR (TTL 1초)     → 초당 3회 초과 시 RATE_LIMITED
3. idempotency:booking:{orderId} SET NX   → 중복 요청 시 ALREADY_PROCESSED
4. stock:promotionRoomType:{id} DECR      → 재고 0 이하 시 SOLD_OUT
5. 모두 통과 → SUCCESS
```

Redis에 먼저 도달한 요청이 먼저 처리되므로 공정성이 자연스럽게 보장됩니다. 990개의 요청이 Redis에서 즉시 차단되어 DB까지 오지 않으므로 DB 부하가 최소화됩니다.

**최종 선택: Redis Lua Script (주 경로) + DB 비관적 락 (Redis 장애 Fallback)**

Redis 단일 장애점 문제는 Circuit Breaker + DB Fallback으로 해결해 가용성을 유지했습니다.

---

## 쟁점 2. 고가용성 설계 — 00시 TPS 급증 대응

### 상황

평시 50TPS, 프로모션 00시 순간 500~1000TPS. 1~5분간 약 10~30배의 트래픽이 몰립니다.
이 상황에서 시스템이 붕괴하지 않으면서도 10개의 재고가 정확히 소진되어야 합니다.

### 해결 전략 — 4단계 트래픽 필터링

```
클라이언트 1000명
    │
    ▼
[Redis Lua Script] ←─ 1단계: 990명 즉시 차단 (SOLD_OUT / RATE_LIMITED)
    │ 10명만 통과
    ▼
[DB: promotion_room_type UPDATE] ←─ 2단계: 10개 스레드만 DB 진입
[DB: room_availability SELECT FOR UPDATE]
[DB: Booking INSERT]
    │
    ▼
[PG 결제] ←─ 3단계: 10개 결제만 실행
    │
    ▼
[Resilience4j PG Circuit Breaker] ←─ 4단계: PG 장애 시 Thread Pool 보호
```

**1단계 — Redis 조기 차단**: 재고 소진 후 요청은 DB 미접근으로 즉시 반환됩니다.

**2단계 — DB 처리 최소화**: Redis 선점 성공한 10건만 DB 트랜잭션을 실행합니다. 락 경합 구간에 10개 스레드만 진입하므로 Lock contention이 없습니다.

**3단계 — PG 호출 격리**: DB 락은 Booking INSERT 이후 해제됩니다. PG 호출(1~3초) 동안 DB 락을 들고 있지 않으므로 다른 요청 블로킹이 없습니다. (DB 락 구간에 이미 10개만 진입하므로 트랜잭션 분리도 불필요합니다.)

**4단계 — PG Circuit Breaker**: 실패율 60% 또는 느린 호출(3초 이상) 50% 초과 시 회로를 오픈합니다. Spring 스레드가 PG 응답을 기다리며 Thread Pool이 고갈되는 것을 차단합니다.

---

## 쟁점 3. Redis 장애 Fallback — Bulkhead로 DB 보호

### 상황

Redis가 다운되면 재고 선점을 위한 모든 요청이 DB로 쏟아집니다. 1000TPS 환경에서 Redis 없이 DB가 직접 받으면 Connection Pool 고갈 및 서버 다운으로 이어집니다.

### 선택지

| 방식 | 설명 | 트레이드오프 |
|------|------|-------------|
| 서비스 전체 중단 | Redis 장애 = 이벤트 중단 | 가용성 포기 |
| 모든 요청을 DB로 통과 | Fallback 없이 DB 직접 처리 | DB 과부하 → 전체 서비스 다운 |
| Circuit Breaker + Bulkhead | Redis 장애 감지 후 제한된 수만 DB Fallback | DB 보호 + 가용성 유지 |

### 왜 그렇게 판단했는지

**Circuit Breaker (redisCircuitBreaker)**
- 슬라이딩 윈도우 10건 중 실패율 50% 또는 느린 호출(500ms 이상) 80% 초과 시 OPEN 상태로 전환됩니다.
- OPEN 시 즉시 fallback으로 전환하고 Redis 회복을 대기합니다. (10초 후 HALF_OPEN)

**Bulkhead (promotionDbFallback)**
- `max-concurrent-calls: 10`, `max-wait-duration: 0ms`
- DB Fallback 동시 진입을 인스턴스당 최대 10개로 제한합니다.
- 초과 요청은 즉시 `BulkheadFullException` → `SERVICE_UNAVAILABLE`로 반환합니다.

### 분산 환경 (3대 서버) 시나리오

```
Redis 장애 발생 시 — 서버 3대, 1000TPS

서버A: Bulkhead(10) → DB에 최대 10개 동시 진입
서버B: Bulkhead(10) → DB에 최대 10개 동시 진입
서버C: Bulkhead(10) → DB에 최대 10개 동시 진입
                       ──────────────────────────
                       합산 최대 30개 DB 동시 요청
                       나머지 970개 → SERVICE_UNAVAILABLE 즉시 반환
```

**30개 경합이 DB에 문제가 없는 이유**: DB Fallback에서 실행하는 `decreaseStockById`는 단순 UPDATE 한 줄이라 락 보유 시간이 수 ms 수준입니다. 30개 스레드가 줄 서더라도 마지막 스레드 대기 시간은 30 × 수ms = 약 60~100ms 수준이며, stock=0 이후 나머지는 `WHERE stock > 0` 조건으로 즉시 반환됩니다. 970개는 Bulkhead에서 이미 차단됐으므로 DB에 도달하지 않습니다.

**정합성 보장**: `decreaseStockById`의 `WHERE stock > 0` 조건이 원자적으로 실행되므로 30개가 경합해도 초과판매가 발생하지 않습니다.

**DB Fallback에서 추가로 수행하는 것**: `findWithLockById`로 `SELECT FOR UPDATE`를 먼저 실행해 프로모션 시간 유효성을 검증한 뒤 재고를 차감합니다. 이는 Redis에서 하던 시간 검증을 DB 경로에서도 보장하기 위함입니다.

---

## 쟁점 4. 멱등성 보장 — orderId 일원화

### 상황

네트워크 타임아웃, 클라이언트 재시도 등으로 동일한 예약 요청이 여러 번 들어올 수 있습니다. 같은 요청이 두 번 처리되면 결제가 이중으로 발생하고 재고도 두 번 차감됩니다.

### 선택지

- **A. 별도 Idempotency-Key 헤더**: HTTP 헤더로 멱등성 키를 받습니다.
- **B. orderId를 멱등성 키로 일원화**: Checkout에서 발급한 orderId를 예약/결제/멱등성에 재사용합니다.

### 왜 그렇게 판단했는지

별도 헤더는 클라이언트가 키를 직접 생성해야 하므로 UUID 충돌이나 키 분실 가능성이 있습니다. PG 결제 요청 키와 멱등성 키가 달라지면 결제 결과 조회 시 매핑이 복잡해집니다.

**orderId 일원화**: Checkout API에서 `ORD-yyyyMMdd-UUID` 형식의 orderId를 서버가 발급합니다. 이 키가 PG 결제 요청 키, DB `booking.order_id`(UNIQUE), Redis 멱등성 키(`idempotency:booking:{orderId}`)에 모두 동일하게 사용됩니다. 하나의 키로 세 가지를 보장하므로 추적이 단순하고, Checkout 캐시에 금액도 함께 저장해 금액 위변조 방지까지 겸합니다.

멱등성 계층:
1. **Redis**: `SET NX`로 PROCESSING 중복 차단 (TTL 30초)
2. **DB**: `booking.order_id UNIQUE` 제약으로 INSERT 수준에서 최종 방어 (Redis 장애 시에도 동작)

---

## 쟁점 5. PG 결제 실패 처리 — PENDING 유지 vs. 즉시 롤백

### 상황

PG 결제 요청 후 응답이 오지 않는 경우(타임아웃, 네트워크 단절)가 있습니다. 이 상황에서 DB를 롤백하면 결제는 됐는데 예약이 없는 상태가 될 수 있습니다.

### 선택지

- **A. 항상 롤백**: 구현이 단순하지만 결제 완료 후 예약이 사라지는 최악의 상황이 발생할 수 있습니다.
- **B. PENDING 상태로 커밋 유지**: 결과 불분명 시 예약 기록을 남기고 나중에 복구합니다.

### 왜 그렇게 판단했는지

결제가 됐는지 안 됐는지 모르는 상태에서 롤백하면 고객 돈이 빠져나갔는데 예약은 없는 최악의 상황이 됩니다. PENDING을 유지하면 이후 PG 웹훅이나 배치 스캐너로 결제 결과를 재확인해 COMPLETED 또는 FAILED로 전환할 수 있습니다.

Spring `@Transactional(noRollbackFor = PgUncertainException.class)`로 결과 불분명 예외 시에도 트랜잭션을 커밋합니다.

| PG 결과 | 처리 |
|---------|------|
| 명확한 거절 (RETRYABLE — 카드 한도 초과 등) | 즉시 롤백 + Redis 재고 복구 |
| 일시 오류 (TEMPORARY) | Retry 최대 2회, 500ms 간격 → 소진 후 롤백 + Redis 재고 복구 |
| 결과 불분명 (SYSTEM — 타임아웃, 인증 오류) | PENDING 커밋 유지 → 웹훅 수신 or 배치 스캐너로 복구 |

**Retry에서 orderId를 PG 멱등성 키로 사용**하므로, 재시도해도 PG 측에서 중복 청구가 발생하지 않습니다.

---

## 쟁점 6. MySQL 데드락 방지 — 락 획득 순서 확정

### 상황

300 VU 동시 테스트에서 MySQL InnoDB 데드락(Error 1213)이 발생했습니다. MySQL이 트랜잭션을 강제 롤백했는데 Spring의 `noRollbackFor` 처리와 충돌해 commit exception이 터지며 500 에러가 반환됐습니다. Redis 재고는 차감된 채로 남아 재고 누수도 발생했습니다.

### 데드락 원인

초기 코드 순서:
```
1. Booking INSERT → gap lock 발생 (clustered index)
2. promotion_room_type SELECT FOR UPDATE
3. room_availability SELECT FOR UPDATE
```
스레드 A가 gap lock을 들고 B의 행 락을 기다리고, 스레드 B가 행 락을 들고 A의 gap lock을 기다리는 순환 대기가 발생했습니다.

### 해결

**락 획득 순서를 INSERT 전에 확정합니다.**

```
1. promotion_room_type UPDATE (stock -1, 행 수준 락 먼저 획득)
2. room_availability SELECT FOR UPDATE
3. Booking INSERT
```

10개의 Redis 선점 성공 스레드가 1번 `promotion_room_type` 행에서 직렬화됩니다. 락 순서가 항상 동일하므로 순환 대기 자체가 불가능합니다.

데드락을 `catch`로 처리하는 방식은 근본 해결이 아닙니다. MySQL이 롤백을 완료한 시점에 `catch`해봤자 이미 트랜잭션은 종료된 상태이고, Spring이 commit을 시도하면 `Application exception overridden by commit exception`이 발생합니다. 구조 자체를 바꾸는 방향을 선택했습니다.

---

## 쟁점 7. JPA L1 캐시와 재고 차감 — @Modifying UPDATE

### 상황

DB 재고 차감 시 `findWithLockById()`로 엔티티를 로드해 `entity.decreaseStock()`을 호출했는데, 500 VU 테스트 후 `promotion_room_type.stock`이 0이 되지 않고 8개가 남는 문제가 발생했습니다.

### 원인

JPA 1차 캐시(Persistence Context) 문제였습니다. 같은 트랜잭션 내에서 이미 캐시된 엔티티가 있으면 `SELECT FOR UPDATE`를 보내도 캐시에서 stale 값을 반환합니다. 여러 스레드가 모두 `stock = 10`으로 읽고 `stock = 9`를 flush하면 마지막 쓰기만 살아남습니다. 트랜잭션 종료 시 dirty check가 캐시의 stale 값을 DB에 덮어쓰는 것도 동일한 문제였습니다.

### 해결

JPQL UPDATE로 DB에서 직접 원자적으로 차감합니다.

```java
@Modifying(clearAutomatically = true)
@Query("UPDATE PromotionRoomType p SET p.stock = p.stock - 1 WHERE p.id = :id AND p.stock > 0")
int decreaseStockById(@Param("id") Long id);
```

- `SET stock = stock - 1`: DB 현재 값 기준으로 차감하므로 캐시를 우회합니다.
- `WHERE stock > 0`: 음수 방지, 재고 소진 시 UPDATE 0건을 반환합니다.
- `clearAutomatically = true`: UPDATE 후 1차 캐시를 즉시 무효화해 dirty check 덮어쓰기를 차단합니다.
- 반환값 0 = 재고 소진으로 즉시 판단하므로 추가 SELECT가 불필요합니다.

---

## 쟁점 8. 복합 결제 설계 — 전략 패턴

### 상황

결제 수단이 신용카드, Y페이, Y포인트 세 가지이고, 조합에 따라 허용/불허가 다릅니다. PG 연동이 필요한 수단과 내부 DB 처리만 필요한 수단이 섞여 있습니다.

### 선택지

- **A. if-else 분기**: 빠르게 구현 가능하지만 결제 수단 추가 시 분기가 복잡해집니다.
- **B. 전략 패턴 (PaymentProcessor interface)**: 각 결제 수단을 독립 클래스로 분리합니다.

### 왜 그렇게 판단했는지

결제 수단은 사업 정책에 따라 자주 추가·변경됩니다. if-else는 `PaymentType` 추가 시 기존 분기 코드를 수정해야 해 OCP를 위반합니다.

전략 패턴으로 `PaymentProcessor` 인터페이스를 정의하고 각 수단을 독립 빈으로 등록했습니다. `PaymentProcessorRegistry`가 타입별로 프로세서를 조회합니다. 새 결제 수단 추가 시 구현 클래스 하나만 추가하면 됩니다.

복합 결제 허용 조합 검증(`validatePaymentCombination`)은 별도 `PaymentValidator`에 격리해, 조합 규칙 변경이 결제 실행 로직에 영향을 주지 않도록 분리했습니다.

| 조합 | 허용 |
|------|------|
| 신용카드 + Y포인트 | ✅ |
| Y페이 + Y포인트 | ✅ |
| 신용카드 + Y페이 | ❌ (PG 중복 불가) |

---

## 쟁점 9. PG Circuit Breaker — 카드 거절 예외를 실패로 집계하지 않는 이유

### 상황

PG Circuit Breaker는 PG 호출 실패율이 60%를 넘으면 회로를 오픈합니다. 선착순 이벤트에서 카드 한도 초과 등의 결제 거절이 몰릴 수 있는데, 이를 PG 인프라 장애와 동일하게 집계하면 정상 사용자까지 결제가 차단됩니다.

### 해결

```yaml
pgCircuitBreaker:
  ignore-exceptions:
    - com.jung.reservation.payment.application.exception.PgCardRejectedException
```

카드 거절(`PgCardRejectedException`)은 PG 서버가 정상 응답한 것이므로 CB 실패 카운트에서 제외했습니다. 타임아웃이나 5xx는 PG 서버 문제이므로 정상 집계합니다. 이를 통해 사용자 측 문제와 인프라 문제를 분리해 CB의 정확도를 높였습니다.

---

## 쟁점 10. 헥사고날 아키텍처 도입 — 비용 대비 효과

### 상황

Redis, PG, DB가 혼재하고 각 외부 시스템마다 장애 대응 전략이 다릅니다. 레이어드 아키텍처라면 Service가 `RedisTemplate`, `JpaRepository`, `FeignClient`에 직접 의존해 인프라 변경과 테스트가 어려워집니다.

### 트레이드오프

| | 레이어드 아키텍처 | 헥사고날 아키텍처 |
|--|---|---|
| 초기 개발 속도 | 빠름 | 느림 (포트/어댑터 보일러플레이트) |
| 인프라 교체 시 | Service 수정 필요 | Adapter만 교체 |
| 단위 테스트 | 전체 인프라 필요 | Spring Context 없이 순수 JUnit |
| 장애 대응 로직 격리 | Service에 혼재 | Application 레이어에 격리 |

### 왜 그렇게 판단했는지

이 시스템은 Redis Fallback, PG Circuit Breaker, DB 비관적 락이 공존하는 복잡한 인프라 구조입니다. Service가 `RedisTemplate`에 직접 의존하면 Redis를 교체하거나 Fallback 전략을 바꿀 때 비즈니스 로직까지 건드려야 합니다. 헥사고날로 OutputPort 인터페이스를 두면 Application 로직은 그대로 두고 Adapter만 교체할 수 있습니다. 도메인 단위 테스트는 Spring Context 없이 순수 JUnit으로 실행 가능해 테스트 피드백이 빠릅니다. 초기 보일러플레이트 비용을 감수하고 장애 대응 로직의 격리와 테스트 용이성을 확보했습니다.

---

## 쟁점 11. Resilience4j 도입 — 자체 구현 vs. 라이브러리

### 상황

Circuit Breaker, Retry, Bulkhead를 모두 적용해야 했습니다. 자체 구현과 Resilience4j 중 선택이 필요했습니다.

### 선택지

- **A. 자체 구현**: `AtomicInteger`, `ConcurrentHashMap`으로 직접 구현합니다.
- **B. Resilience4j**: Spring Boot Starter로 선언적으로 적용합니다.

### 왜 그렇게 판단했는지

자체 구현은 슬라이딩 윈도우, HALF_OPEN 상태 전환, 스레드 세이프한 카운터 관리를 모두 직접 구현해야 해서 버그 위험이 높습니다. Resilience4j는 `@CircuitBreaker`, `@Retry`, `@Bulkhead` 어노테이션 하나로 선언적으로 적용 가능하고, `application.yml`에서 파라미터를 관리해 코드 수정 없이 임계값을 조정할 수 있습니다. 프로덕션 검증된 라이브러리를 채택해 인프라 레이어 구현에 집중하는 대신 비즈니스 로직에 집중했습니다.
