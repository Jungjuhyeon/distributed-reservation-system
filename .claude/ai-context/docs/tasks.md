# Tasks

## Phase 1: 도메인 모델 & 기본 CRUD
- [x] BaseEntity 생성 (common/entity, @MappedSuperclass)
- [x] @EnableJpaAuditing 설정
- [x] Accommodation 엔티티 생성
- [x] RoomType 엔티티 생성
- [x] RoomAvailability 엔티티 생성
- [x] Promotion 엔티티 생성
- [x] PromotionRoomType 엔티티 생성
- [x] User 엔티티 생성
- [x] UserPoint 엔티티 생성
- [x] PointHistory 엔티티 생성
- [x] Booking 엔티티 생성 (order_id UNIQUE, promotion_room_type_id nullable)
- [x] Payment 엔티티 생성
- [x] 각 엔티티별 Repository 생성 (infra/persistence)
- [x] GlobalExceptionHandler 생성
- [x] bootRun 후 테이블 생성 확인

## Phase 2: Checkout API
- [x] RedisConfig 설정 (RedisTemplate, 직렬화)
- [x] CheckoutResponse DTO 생성
- [x] CheckoutController 생성 (GET /api/checkout/{roomTypeId}?userId&promotionRoomTypeId=optional)
- [x] CheckoutService 생성 (CheckoutUseCase + CheckoutInputPort)
- [x] orderId 생성 로직 (ORD-yyyyMMdd-UUID)
- [x] Redis 주문서 캐시 저장 (checkout:{orderId} → {amount}, TTL 10분)
- [x] API 호출 테스트 (상품/포인트/orderId 반환 확인)

## Phase 3: Redis Lua Script (재고 정합성 + 공정성 + 멱등성)
- [x] Lua Script 작성 (시간검증 → Rate Limit → 재고차감)
- [x] StockOutputPort + StockRedisAdapter + StockLuaScript
- [x] Lua Script에 멱등성 통합 (시간검증 → Rate Limit → 멱등성 → 재고차감)
- [x] 성공 시 COMPLETED 상태 변경 (TTL 10분)
- [x] 실패 시 멱등성 키 삭제 (DEL)
- [x] 멀티스레드 동시 요청 테스트 (초과판매 없음 확인)
- [x] 오픈 전 요청 차단 테스트
- [x] Rate Limit 동작 테스트
- [x] 멱등성 테스트 (동일 orderId 중복 요청 차단)

## Phase 4: Booking API 기본 플로우
- [x] BookingRequest DTO 생성
- [x] BookingResponse DTO 생성
- [x] BookingController 생성 (POST /api/booking)
- [x] BookingService 생성 (BookingInputPort)
- [x] 사전 금액 검증 (checkout 캐시 amount vs totalAmount)
- [x] Lua Script 실행 통합 (Phase 3)
- [x] PaymentProcessor 인터페이스 정의 (pay, cancel)
- [x] YPointPaymentProcessor 구현 (포인트 차감)
- [x] PaymentProcessorRegistry 구현
- [x] Booking + Payment DB 저장
- [x] 포인트 단건 결제 E2E 테스트

## Phase 5: 결제 확장 (복합 결제 + PG Mock)
- [x] PgClient 인터페이스 정의
- [x] PgClient Mock 구현체 작성
- [x] CreditCardPaymentProcessor 구현
- [x] YPayPaymentProcessor 구현
- [x] 복합 결제 validation (주결제 수단 1개만 + Y포인트 조합만 허용)
- [x] paymentMethods 배열 순회 결제 처리
- [x] 부분 실패 시 보상 트랜잭션 (역순 cancel)
- [x] 신용카드+포인트 복합 결제 테스트
- [x] 부분 실패 롤백 테스트

## Phase 6: 장애 대응
- [x] 결제 실패 시 Redis 재고 복구 (INCR)
- [x] 포인트 차감 후 PG 실패 시 포인트 환불 (보상 트랜잭션에서 처리됨)
- [x] build.gradle에 resilience4j 의존성 추가
- [x] Resilience4jConfig 설정
- [x] Circuit Breaker 적용 (Redis 장애 감지)
- [x] DB 비관적 락 Fallback 구현 (room_availability 비관적 락 적용, 프로모션은 Fail-Fast)
- [x] Redis 다운 시 Fallback 동작 테스트 (Circuit Breaker 강제 OPEN → 503 확인)

## Phase 7: 테스트 & 검증
- [ ] 단위 테스트: 각 Service
- [ ] 단위 테스트: 각 PaymentProcessor
- [ ] 통합 테스트: Checkout → Booking 전체 플로우
- [ ] 동시성 테스트: 100개 동시 요청 → 10개만 성공
- [ ] 멱등성 테스트: 동일 orderId 중복 요청
- [ ] 결제 실패 테스트: PG 실패 시 재고 복구
- [ ] Redis 장애 테스트: DB Fallback
- [ ] DECISIONS.md 작성
