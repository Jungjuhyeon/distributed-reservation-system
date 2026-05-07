# DECISIONS.md — 기술적 판단과 트레이드오프

---

## 쟁점 1. 선착순 재고 처리 — Redis Lua Script vs. DB 비관적 락

### 상황
평시 50TPS이지만 프로모션이 시작되는 00시에 1~5분간 순간적으로 500~1000TPS가 예상된다.
재고는 10개뿐이고 수천 명이 동시에 예약을 시도하므로, 두 가지를 동시에 보장해야 한다.

- **정합성**: 재고가 10개를 초과하거나 음수가 되는 초과판매가 절대 발생하지 않아야 한다.
- **공정성**: 먼저 요청한 사용자가 먼저 재고를 가져가야 한다. 느린 사용자가 운 좋게 성공하거나, 빠른 사용자가 탈락하는 일이 없어야 한다.

### 선택지

| 방식 | 정합성 | 공정성 | 성능 |
|------|--------|--------|------|
| DB 비관적 락 (SELECT FOR UPDATE) | ✅ 보장 | ✅ 락 대기 순서 | ❌ 락 경합으로 TPS 급락 |
| DB 낙관적 락 (version) | ⚠️ 재시도 필요 | ❌ 재시도 순서 불확정 | ⚠️ 충돌 많을수록 재시도 폭풍 |
| Redis Lua Script | ✅ 원자적 보장 | ✅ Redis 도달 순서 = 처리 순서 | ✅ 마이크로초 응답 |

### 왜 그렇게 판단했는지

**DB 비관적 락**은 정합성은 완벽하지만, 1000개의 트랜잭션이 동일 행에서 줄 서면 락 대기 시간이 누적되고 Connection Pool이 고갈된다. 500~1000TPS를 단일 행 락으로 처리하면 TPS가 수십 단위로 떨어진다. 실제로 300 VU 테스트에서 MySQL 데드락(Error 1213)이 발생해 500 에러가 터졌다.

**DB 낙관적 락**은 고경쟁 환경에서 대부분이 충돌 후 재시도하며 재시도 폭풍이 발생한다. 재시도 순서가 보장되지 않아 공정성도 깨진다.

**Redis Lua Script**는 Redis 싱글 스레드 위에서 Lua Script가 원자적으로 실행된다. 시간 검증 → Rate Limit → 멱등성 → 재고 차감을 하나의 스크립트로 묶어 Race Condition 없이 처리한다. Redis에 먼저 도달한 요청이 먼저 처리되므로 공정성이 자연스럽게 보장된다. 응답 속도가 마이크로초 단위라 1000TPS도 큐 없이 처리 가능하다.

**최종 선택: Redis Lua Script (주 경로) + DB 비관적 락 (Redis 장애 Fallback)**

Redis 단일 장애점 문제는 Circuit Breaker + DB Fallback으로 해결해 가용성을 유지했다.

---

## 쟁점 2. 고가용성 설계 — 00시 TPS 급증 대응

### 상황
평시 50TPS, 프로모션 00시 순간 500~1000TPS. 1~5분간 약 10~30배의 트래픽이 몰린다.
이 상황에서 시스템이 붕괴하지 않으면서도 10개의 재고가 정확히 소진되어야 한다.

### 핵심 문제

1000TPS가 모두 DB까지 도달하면 MySQL Connection Pool, 트랜잭션 락, 디스크 I/O가 한꺼번에 포화된다.
재고 10개가 소진된 후에도 990개 이상의 요청이 계속 DB를 두드리면 불필요한 부하가 발생한다.

### 해결 전략

**1단계 — Redis에서 조기 차단**

Redis Lua Script가 최전선에서 요청을 걸러낸다.
- 재고 소진 → 즉시 SOLD_OUT 반환 (DB 미접근)
- Rate Limit 초과 → 즉시 RATE_LIMITED 반환 (DB 미접근)
- 중복 요청 → 즉시 ALREADY_PROCESSED 반환 (DB 미접근)

10개를 제외한 490~990개의 요청이 Redis에서 차단되어 DB까지 오지 않는다.

**2단계 — DB 처리는 최소 10건으로 한정**

Redis 선점에 성공한 10건만 DB 트랜잭션(재고 차감 → Booking INSERT → 결제 → COMPLETED)을 실행한다.

**3단계 — Redis 장애 시 Bulkhead로 DB 보호**

Redis 장애 시 DB Fallback으로 전환되는데, 이때 모든 요청이 DB로 쏟아지면 DB가 다운된다.
Bulkhead(최대 동시 10개)로 DB Fallback 진입 자체를 제한해 DB를 보호한다. 제한을 초과한 요청은 즉시 SERVICE_UNAVAILABLE로 반환한다.

**4단계 — PG Circuit Breaker로 PG 장애 전파 차단**

PG가 느려져도 Spring 스레드가 PG 응답을 기다리며 대기하면 Thread Pool이 고갈된다.
Circuit Breaker(실패율 60%, 느린 호출 50%)로 PG 장애를 감지하고 즉시 회로를 열어 Thread Pool 고갈을 막는다.

### 트레이드오프

Redis를 앞단에 두면 Redis 자체가 단일 장애점이 된다. 이를 Circuit Breaker + DB Fallback으로 보완했지만, DB Fallback 상태에서는 TPS가 크게 떨어진다. 프로모션 이벤트에서 Redis 장애는 치명적이므로, 운영 시 Redis 이중화(Redis Sentinel/Cluster)를 추가로 고려해야 한다.

---

## 쟁점 3. 멱등성 보장 — orderId 일원화

### 상황
네트워크 타임아웃, 클라이언트 재시도 등으로 동일한 예약 요청이 여러 번 들어올 수 있다. 같은 요청이 두 번 처리되면 결제가 이중으로 발생하고 재고도 두 번 차감된다.

### 선택지
- **A. 별도 Idempotency-Key 헤더**: HTTP 헤더로 멱등성 키를 받음
- **B. orderId를 멱등성 키로 일원화**: Checkout에서 발급한 orderId를 예약/결제/멱등성에 재사용

### 왜 그렇게 판단했는지

별도 헤더는 클라이언트가 키를 직접 생성해야 하므로 UUID 충돌이나 키 분실 가능성이 있다. PG 결제 요청 키와 멱등성 키가 달라지면 결제 결과 조회 시 매핑이 복잡해진다.

**orderId 일원화**: Checkout API에서 `ORD-yyyyMMdd-UUID` 형식의 orderId를 서버가 발급한다. 이 키가 PG 결제 요청 키, DB `booking.order_id`(UNIQUE), Redis 멱등성 키(`idempotency:booking:{orderId}`)에 모두 동일하게 쓰인다. 하나의 키로 세 가지를 보장하므로 추적이 단순하고, Checkout 캐시에 금액도 함께 저장해 금액 위변조 방지까지 겸한다.

멱등성 키는 Redis 장애 시 DB `booking.order_id UNIQUE` 제약이 최종 방어선이 된다.

---

## 쟁점 4. PG 결제 실패 처리 — PENDING 유지 vs. 즉시 롤백

### 상황
PG 결제 요청 후 응답이 오지 않는 경우(타임아웃, 네트워크 단절)가 있다. 이 상황에서 DB를 롤백하면 결제는 됐는데 예약이 없는 상태가 될 수 있다.

### 선택지
- **A. 항상 롤백**: 구현이 단순하지만 결제 완료 후 예약이 사라지는 최악의 사고 가능
- **B. PENDING 상태로 커밋 유지**: 결과 불분명 시 예약 기록을 남기고 나중에 복구

### 왜 그렇게 판단했는지

결제가 됐는지 안 됐는지 모르는 상태에서 롤백하면 고객 돈이 빠져나갔는데 예약은 없는 최악의 상황이 된다. 반면 PENDING을 유지하면 이후 PG 웹훅이나 배치 스캐너로 결제 결과를 재확인해 COMPLETED 또는 FAILED로 전환할 수 있다.

Spring `@Transactional(noRollbackFor = PgUncertainException.class)`로 결과 불분명 예외 시에도 트랜잭션을 커밋한다. PG가 명확히 거절한 경우(카드 한도 초과, 일시 오류)는 일반 롤백으로 재고를 복구한다.

| PG 결과 | 처리 |
|---------|------|
| 명확한 거절 (RETRYABLE) | 즉시 롤백 + Redis 재고 복구 |
| 일시 오류 (TEMPORARY) | Retry 2회 후 롤백 + Redis 재고 복구 |
| 결과 불분명 (SYSTEM) | PENDING 커밋 유지 → 웹훅/배치 복구 |

---

## 쟁점 5. MySQL 데드락 방지 — 락 획득 순서 확정

### 상황
300 VU 동시 테스트에서 MySQL InnoDB 데드락(Error 1213)이 발생했다. MySQL이 트랜잭션을 강제 롤백했는데 Spring의 `noRollbackFor` 처리와 충돌해 commit exception이 터지며 500 에러가 반환됐다. Redis 재고는 차감된 채로 남아 재고 누수도 발생했다.

### 데드락 원인

초기 코드 순서:
```
1. Booking INSERT (PK로 clustered index, gap lock 발생 가능)
2. promotion_room_type SELECT FOR UPDATE
3. room_availability SELECT FOR UPDATE
```
스레드 A와 B가 서로 다른 순서로 락을 잡으며 순환 대기가 발생했다.

### 해결

**락 획득 순서를 INSERT 전에 확정한다.**

```
1. promotion_room_type UPDATE (stock -1, 행 수준 락 먼저 획득)
2. room_availability SELECT FOR UPDATE
3. Booking INSERT
```

10개의 Redis 선점 성공 스레드가 1번 `promotion_room_type` 행에서 직렬화된다. 락 순서가 항상 동일하므로 순환 대기 자체가 불가능하다. 데드락 catch 처리는 땜질이고 catch 시점에는 이미 MySQL이 롤백을 완료해 commit이 실패하므로, 구조 자체를 바꾸는 방향을 택했다.

---

## 쟁점 6. JPA L1 캐시와 재고 차감 — @Modifying UPDATE

### 상황
DB 재고 차감 시 `findWithLockById()`로 엔티티를 로드해 `entity.decreaseStock()`을 호출했는데, 500 VU 테스트 후 `promotion_room_type.stock`이 0이 되지 않고 8개가 남는 문제가 발생했다.

### 원인

JPA 1차 캐시 문제였다. 같은 트랜잭션 내에서 이미 캐시된 엔티티가 있으면 `SELECT FOR UPDATE`를 보내도 캐시에서 stale 값을 반환한다. 여러 스레드가 모두 `stock = 10`으로 읽고 `stock = 9`를 DB에 flush하면 마지막 쓰기만 살아남아 stock이 제대로 줄지 않는다. 트랜잭션 종료 시 JPA dirty check가 캐시의 stale 값을 DB에 덮어쓰는 것도 문제였다.

### 해결

JPQL UPDATE로 DB에서 직접 원자적으로 차감한다.

```java
@Modifying(clearAutomatically = true)
@Query("UPDATE PromotionRoomType p SET p.stock = p.stock - 1 WHERE p.id = :id AND p.stock > 0")
int decreaseStockById(@Param("id") Long id);
```

- `SET stock = stock - 1`: DB가 현재 값 기준으로 차감, 캐시 우회
- `WHERE stock > 0`: 음수 방지 (재고 소진 시 UPDATE 0건 반환)
- `clearAutomatically = true`: UPDATE 후 1차 캐시 즉시 무효화, dirty check 덮어쓰기 차단
- 반환값 0 = 재고 소진으로 즉시 판단, 추가 SELECT 불필요

---

## 쟁점 7. 복합 결제 설계 — 전략 패턴

### 상황
결제 수단이 신용카드, Y페이, Y포인트 세 가지이고, 조합에 따라 허용/불허가 다르다. PG 연동이 필요한 수단과 내부 DB 처리만 필요한 수단이 섞여 있다.

### 선택지
- **A. if-else 분기**: 빠르게 구현 가능하지만 결제 수단 추가 시 분기가 복잡해짐
- **B. 전략 패턴 (PaymentProcessor interface)**: 각 결제 수단을 독립 클래스로 분리

### 왜 그렇게 판단했는지

결제 수단은 사업 정책에 따라 자주 추가/변경된다. if-else는 `PaymentType` 추가 시 기존 분기 코드를 수정해야 해 OCP를 위반한다.

전략 패턴으로 `PaymentProcessor` 인터페이스를 정의하고 각 수단을 독립 빈으로 등록한다. `PaymentProcessorRegistry`가 타입별 프로세서를 주입받아 조회한다. 복합 결제 허용 조합 검증(`validatePaymentCombination`)은 별도 `PaymentValidator`에 격리해 조합 규칙 변경이 다른 코드에 영향을 주지 않도록 했다.

---

## 쟁점 8. PG Circuit Breaker — 카드 거절 예외를 실패로 집계하지 않는 이유

### 상황
PG Circuit Breaker는 PG 호출 실패율이 60%를 넘으면 회로를 연다. 그런데 "카드 한도 초과"처럼 PG 서버는 정상인데 사용자 카드 문제로 실패하는 경우도 있다.

### 문제

카드 거절을 일반 실패로 집계하면, 사용자 카드 문제가 많은 이벤트에서 CB가 열려 정상 사용자까지 결제가 막힌다.

### 해결

`ignoreExceptions: PgCardRejectedException` — 카드 거절은 CB 실패 카운트에서 제외한다. PG 서버 자체는 정상 동작한 것이고, 실패의 원인이 인프라가 아니라 사용자 데이터이므로 CB 통계에 포함시키지 않는다. 타임아웃이나 5xx는 PG 서버 문제이므로 정상 집계한다.

---

## 쟁점 9. 헥사고날 아키텍처 도입 — 비용 대비 효과

### 상황
Redis, PG, DB가 혼재하고 각 외부 시스템마다 장애 대응 전략이 다르다. 레이어드 아키텍처라면 Service가 `RedisTemplate`, `JpaRepository`, `FeignClient`에 직접 의존해 인프라 교체나 테스트가 어려워진다.

### 트레이드오프

**레이어드**: 초기 개발 속도가 빠르다. 하지만 Service가 인프라에 직접 의존하면 테스트 시 전체 인프라가 필요하고, Redis를 교체하거나 PG를 바꾸면 Service 코드를 수정해야 한다.

**헥사고날**: 초기 클래스 수가 많아 개발 비용이 높다. 하지만 Domain과 Application은 OutputPort 인터페이스에만 의존하므로, Redis Adapter를 교체해도 Application 로직은 변경이 없다. 도메인 단위 테스트는 Spring Context 없이 순수 JUnit으로 실행 가능해 테스트가 빠르다.

### 왜 그렇게 판단했는지

이 시스템은 Redis Fallback, PG Circuit Breaker, DB 비관적 락이 공존하는 복잡한 인프라 구조다. 인프라 복잡도가 높을수록 헥사고날의 이점이 커진다. 초기 보일러플레이트 비용을 감수하고 장애 대응 로직을 Application 레이어에 격리해 유지보수성과 테스트 용이성을 확보했다.
