# 개발 계획: 초특가 숙소 선착순 예약 시스템

단순한 것부터 점진적으로 기능을 추가하는 방식으로 개발한다.

---

## Phase 1: 도메인 모델 & 기본 CRUD

**목표**: JPA 엔티티와 기본 조회가 동작하는 뼈대 구축

**구현 항목**
- `BaseEntity` (common/entity) — `@MappedSuperclass`, createdAt/updatedAt 감사 필드
- `@EnableJpaAuditing` 설정
- 숙박 도메인 Entity: `Accommodation`, `RoomType`, `RoomAvailability`
- 프로모션 도메인 Entity: `Promotion`, `PromotionRoomType`
- 주문/결제 도메인 Entity: `User`, `Booking`, `Payment`
- Repository: 각 엔티티별 `JpaRepository`
- DTO: `CheckoutResponse`, `BookingRequest`, `BookingResponse`
- `GlobalExceptionHandler` (공통 에러 응답 형식)
- `data.sql`로 테스트용 초기 데이터 삽입

**기술**
- Spring Data JPA (`@Entity`, `@Enumerated`, `@Column`)
- Lombok (`@Getter`, `@NoArgsConstructor`)
- private 생성자 + `static create()` 팩토리 메서드 (Builder 대신)
- `spring.jpa.hibernate.ddl-auto=update`로 스키마 자동 생성
- `@RestControllerAdvice` + `@ExceptionHandler`

**완료 기준**: `./gradlew bootRun` 후 테이블 생성 확인, 초기 데이터 삽입 확인

---

## Phase 2: Checkout API (주문서 진입)

**목표**: GET /api/checkout/{productId} 동작

**구현 항목**
- `CheckoutController` → `CheckoutService`
- 상품 정보 + 사용자 포인트 조회 (DB)
- `orderId` 생성 로직 (`ORD-yyyyMMdd-UUID`)
- Redis에 주문서 캐시 저장 (`checkout:{orderId}` → `{amount, userId}`, TTL 10분)
- `RedisConfig` (RedisTemplate 빈 설정)

**기술**
- Spring Data Redis (`RedisTemplate`, `ValueOperations`)
- `RedisConfig`에서 `RedisConnectionFactory`, 직렬화 설정 (`GenericJackson2JsonRedisSerializer`)
- UUID 생성: `java.util.UUID.randomUUID()`

**완료 기준**: API 호출 시 상품/포인트 정보 + orderId 반환, Redis에 캐시 저장 확인

---

## Phase 3: Redis Lua Script (재고 정합성 + 공정성)

**목표**: 선착순 재고 차감의 원자적 처리, 오픈 시간 검증, Rate Limiting

**구현 항목**
- `StockService`: Lua Script 로딩 및 실행
- Lua Script 작성 (`stock.lua`)
  - `rate_limit:{userId}` 초과 → RATE_LIMITED
  - `sale_start:promotion:{promotionId}` vs `Redis TIME` → NOT_STARTED
  - `stock:promotionRoomType:{id}` DECR → 0 미만이면 복구 후 SOLD_OUT
  - 통과 → rate_limit 증가 → SUCCESS
- 앱 기동 시 Redis에 프로모션 재고/판매시작시간 초기화 (`ApplicationRunner`)

**기술**
- `RedisScript<String>` + `DefaultRedisScript` (Lua Script 로딩)
- `RedisTemplate.execute(RedisScript, keys, args)` 로 실행
- Redis `TIME` 명령으로 서버 시간 통일 (분산 환경 공정성)
- Lua Script 내 `redis.call('TIME')`, `DECR`, `INCR`, `SET EX`

**완료 기준**: 멀티스레드 동시 요청 시 재고 초과판매 없음 확인, 오픈 전 요청 차단 확인

---

## Phase 4: 멱등성 처리

**목표**: 결제 버튼 연타/새로고침에 의한 중복 주문 방지

**구현 항목**
- Booking API 진입 시 `idempotency:booking:{orderId}` SET NX (TTL 30초, PROCESSING)
- 이미 존재하면 기존 결과 반환 (연타 차단)
- 결제 성공 → TTL 10분 COMPLETED로 갱신
- 결제 실패 → 키 삭제 (DEL)
- DB `booking.order_id` UNIQUE 제약으로 영구 중복 방지

**기술**
- `RedisTemplate.opsForValue().setIfAbsent(key, value, timeout)` (SET NX EX)
- `RedisTemplate.expire()` (TTL 갱신)
- `RedisTemplate.delete()` (실패 시 삭제)
- JPA `@Column(unique = true)` on `booking.order_id`

**완료 기준**: 동일 orderId 연속 요청 시 단일 처리, 실패 후 재시도 가능 확인

---

## Phase 5: Booking API 기본 플로우 (포인트 단건 결제)

**목표**: POST /api/booking의 기본 결제 + 주문 생성 동작

**구현 항목**
- `BookingController` → `BookingService`
- 사전 금액 검증: Redis 캐시(`checkout:{orderId}`)의 amount vs 요청 totalAmount
- Phase 3 Lua Script 실행 (재고 차감)
- Phase 4 멱등성 체크 통합
- 포인트 잔액 검증 후 차감
- Booking + Payment DB 저장
- `PaymentProcessor` 인터페이스 정의
- `YPointPaymentProcessor` 구현 (DB 포인트 차감)
- `PaymentProcessorRegistry`: `Map<PaymentType, PaymentProcessor>` 빈 주입

**기술**
- `@Transactional`로 DB 작업 원자성 보장
- 전략 패턴: `PaymentProcessor` 인터페이스 (`pay()`, `cancel()`)

**완료 기준**: 포인트 단건 결제로 주문 생성 성공, 재고 차감 + 포인트 차감 확인

---

## Phase 6: 결제 확장 (복합 결제 + PG Mock)

**목표**: 신용카드/Y페이 PG 결제 + 복합 결제 지원

**구현 항목**
- `CreditCardPaymentProcessor` 구현 (PG 승인 Mock)
- `YPayPaymentProcessor` 구현 (PG 승인 Mock)
- PG 인터페이스: `PgClient` (Mock 구현체)
- 복합 결제: paymentMethods 배열 순회하며 각 processor 호출
- 복합 결제 validation (신용카드 + Y페이 혼용 불가)
- 부분 실패 시 보상 트랜잭션 (성공한 결제 역순 취소)

**기술**
- 전략 패턴 확장: `@Component`로 프로세서 자동 등록
- 보상 트랜잭션: 각 processor의 `cancel()` 역순 호출

**완료 기준**: 신용카드+포인트 복합 결제 성공, 부분 실패 시 롤백 확인

---

## Phase 7: 장애 대응 (결제 실패 보상 + Redis Fallback)

**목표**: 결제 실패 시 재고 복구, Redis 장애 시 DB Fallback

**구현 항목**
- 결제 실패/예외 시 Redis 재고 복구 (`INCR`)
- 포인트 차감 후 PG 실패 → 포인트 환불
- Resilience4j Circuit Breaker 설정
  - Redis 장애 감지 시 DB 비관적 락 Fallback (`SELECT FOR UPDATE`)
  - 멱등성은 DB UNIQUE로 보장
- `Resilience4jConfig` 빈 설정

**기술**
- Resilience4j (`@CircuitBreaker`, `fallbackMethod`)
- `build.gradle`에 `resilience4j-spring-boot3` 의존성 추가
- DB 비관적 락: `@Lock(LockModeType.PESSIMISTIC_WRITE)` + `@Query`
- `application.yml`에 Circuit Breaker 설정 (failureRateThreshold, waitDurationInOpenState 등)

**완료 기준**: PG 실패 시 재고/포인트 정상 복구, Redis 다운 후 DB Fallback 동작 확인

---

## Phase 8: 테스트 & 검증

**목표**: 전체 시나리오 검증

**구현 항목**
- 단위 테스트: 각 Service, PaymentProcessor
- 통합 테스트: Checkout → Booking 전체 플로우
- 동시성 테스트: `ExecutorService` + `CountDownLatch`로 100개 동시 요청 → 10개만 성공
- 멱등성 테스트: 동일 orderId 중복 요청 → 단일 처리
- 결제 실패 테스트: PG Mock 실패 시 재고 복구
- DECISIONS.md 작성

**기술**
- `@SpringBootTest` + Testcontainers (MySQL, Redis)
- `ExecutorService.newFixedThreadPool()` + `CountDownLatch`
- AssertJ (`assertThat()`)

**완료 기준**: 모든 테스트 통과, DECISIONS.md 완성
