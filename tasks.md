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
- [ ] CheckoutResponse DTO 생성
- [ ] BookingRequest DTO 생성
- [ ] BookingResponse DTO 생성
- [ ] GlobalExceptionHandler 생성
- [ ] data.sql 초기 데이터 작성
- [ ] bootRun 후 테이블 생성 및 데이터 확인

## Phase 2: Checkout API
- [ ] RedisConfig 설정 (RedisTemplate, 직렬화)
- [ ] CheckoutController 생성 (GET /api/checkout/{promotionRoomTypeId})
- [ ] CheckoutService 생성
- [ ] orderId 생성 로직 (ORD-yyyyMMdd-UUID)
- [ ] Redis 주문서 캐시 저장 (checkout:{orderId} → {amount, userId}, TTL 10분)
- [ ] API 호출 테스트 (상품/포인트/orderId 반환 확인)

## Phase 3: Redis Lua Script (재고 정합성)
- [ ] stock.lua 작성 (rate_limit → 시간검증 → 재고차감)
- [ ] StockService 생성 (Lua Script 로딩 및 실행)
- [ ] ApplicationRunner로 Redis 프로모션 재고/판매시작시간 초기화
- [ ] 멀티스레드 동시 요청 테스트 (초과판매 없음 확인)
- [ ] 오픈 전 요청 차단 테스트
- [ ] Rate Limit 동작 테스트

## Phase 4: 멱등성 처리
- [ ] idempotency:booking:{orderId} SET NX 구현 (TTL 30초 PROCESSING)
- [ ] 중복 요청 시 기존 결과 반환 로직
- [ ] 성공 시 COMPLETED 상태 변경 (TTL 10분)
- [ ] 실패 시 키 삭제 (DEL)
- [ ] 동일 orderId 연속 요청 테스트

## Phase 5: Booking API 기본 플로우
- [ ] BookingController 생성 (POST /api/booking)
- [ ] BookingService 생성
- [ ] 사전 금액 검증 (checkout 캐시 amount vs totalAmount)
- [ ] Lua Script 실행 통합 (Phase 3)
- [ ] 멱등성 체크 통합 (Phase 4)
- [ ] PaymentProcessor 인터페이스 정의 (pay, cancel)
- [ ] YPointPaymentProcessor 구현 (포인트 차감)
- [ ] PaymentProcessorRegistry 구현
- [ ] Booking + Payment DB 저장
- [ ] 포인트 단건 결제 E2E 테스트

## Phase 6: 결제 확장 (복합 결제 + PG Mock)
- [ ] PgClient 인터페이스 정의
- [ ] PgClient Mock 구현체 작성
- [ ] CreditCardPaymentProcessor 구현
- [ ] YPayPaymentProcessor 구현
- [ ] 복합 결제 validation (신용카드 + Y페이 혼용 불가)
- [ ] paymentMethods 배열 순회 결제 처리
- [ ] 부분 실패 시 보상 트랜잭션 (역순 cancel)
- [ ] 신용카드+포인트 복합 결제 테스트
- [ ] 부분 실패 롤백 테스트

## Phase 7: 장애 대응
- [ ] 결제 실패 시 Redis 재고 복구 (INCR)
- [ ] 포인트 차감 후 PG 실패 시 포인트 환불
- [ ] build.gradle에 resilience4j 의존성 추가
- [ ] Resilience4jConfig 설정
- [ ] Circuit Breaker 적용 (Redis 장애 감지)
- [ ] DB 비관적 락 Fallback 구현 (SELECT FOR UPDATE)
- [ ] Redis 다운 시 Fallback 동작 테스트

## Phase 8: 테스트 & 검증
- [ ] 단위 테스트: 각 Service
- [ ] 단위 테스트: 각 PaymentProcessor
- [ ] 통합 테스트: Checkout → Booking 전체 플로우
- [ ] 동시성 테스트: 100개 동시 요청 → 10개만 성공
- [ ] 멱등성 테스트: 동일 orderId 중복 요청
- [ ] 결제 실패 테스트: PG 실패 시 재고 복구
- [ ] Redis 장애 테스트: DB Fallback
- [ ] DECISIONS.md 작성
