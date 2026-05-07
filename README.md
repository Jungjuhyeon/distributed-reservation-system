# 분산 예약 시스템 (Distributed Reservation System)

초특가 숙소 선착순 예약 시스템. Redis Lua Script 기반 원자적 재고 선점과 헥사고날 아키텍처를 적용한 고가용성 예약 서버입니다.

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| Database | MySQL 8.0 |
| Cache | Redis 7 |
| ORM | Spring Data JPA (Hibernate) |
| 장애 대응 | Resilience4j (Circuit Breaker, Retry, Bulkhead) |
| 부하 테스트 | k6 |
| 컨테이너 | Docker Compose |

---

## 시스템 아키텍처

헥사고날 아키텍처(Ports & Adapters) + DDD를 기반으로 설계했습니다. 도메인이 중심이고 외부 기술(DB, Redis, PG)은 포트 인터페이스를 통해서만 접근합니다.

```
┌──────────────────────────────────────────────────────────┐
│                    Driving Adapters                       │
│         [BookingController]  [CheckoutController]         │
│              [PgWebhookController]                        │
└────────────────────┬─────────────────────────────────────┘
                     │ calls
┌────────────────────▼─────────────────────────────────────┐
│               Application Layer                           │
│  [CheckoutInputPort]      [BookingInputPort]              │
│       │                        │                          │
│  [CheckoutUseCase]         [BookingUseCase]  (Ports)      │
│                                │                          │
│     [PaymentValidator] [PromotionStockService]            │
│     [PaymentExecutionService] [RoomAvailabilityService]   │
└──────────┬───────────────────────────┬────────────────────┘
           │ implements                │ calls
┌──────────▼───────────┐   ┌──────────▼───────────────────┐
│    Domain Layer       │   │      Driven Adapters          │
│  Booking / Payment    │   │  [PromotionRoomTypeAdapter]   │
│  Promotion / User     │   │  [RoomAvailabilityAdapter]    │
│  Accommodation        │   │  [StockRedisAdapter]          │
│                       │   │  [CheckoutCacheAdapter]       │
│  (순수 비즈니스 로직)  │   │  [IdempotencyRedisAdapter]   │
└───────────────────────┘   └──────────────────────────────┘
```

### 도메인 구성

| 도메인 | 책임 |
|--------|------|
| `booking` | 예약 생성, 상태 관리 (PENDING → COMPLETED), Checkout 주문서 발급 |
| `payment` | 복합 결제 처리 (신용카드 / Y페이 / Y포인트), PG 연동, 웹훅 복구 |
| `promotion` | 선착순 재고 관리, Redis Lua Script 원자적 선점, DB Fallback |
| `accommodation` | 숙소/객실 정보, 날짜별 재고(RoomAvailability) 관리 |
| `user` | 회원 정보, 포인트 잔액, 포인트 이력 |

---

## ERD

<img width="1470" height="1132" alt="ERD" src="https://github.com/user-attachments/assets/4375708c-2653-4322-8d22-899409a6188a" />

### 테이블 설명

| 테이블 | 주요 컬럼 | 설명 |
|--------|-----------|------|
| `users` | id, name, phone | 회원 정보 |
| `user_point` | user_id(UNIQUE), current_point | 사용자별 포인트 잔액. user와 1:1 관계 |
| `point_history` | user_point_id, amount, type, booking_id | 포인트 변동 이력. type: USE / REFUND / EARN / BURN |
| `accommodation` | host_id, name, address, description | 숙소 정보. host는 users FK |
| `room_type` | accommodation_id, name, amount, capacity, room_count, check_in_time, check_out_time | 객실 타입. 정가 및 수용 인원 정의 |
| `room_availability` | room_type_id, date, available_count, amount | 날짜별 예약 가능 재고. (room_type_id, date) UNIQUE. 비관적 락으로 동시성 제어 |
| `promotion` | name, start_date_time, end_date_time, daily_start_time, daily_end_time | 프로모션 이벤트. 전체 기간 + 매일 오픈 시간대 관리 |
| `promotion_room_type` | promotion_id, room_type_id, promotion_amount, stock | 프로모션 상품. stock이 Redis `stock:promotionRoomType:{id}`와 동기화됨 |
| `booking` | order_id(UNIQUE), user_id, room_type_id, promotion_room_type_id, check_in_date, check_out_date, status, total_amount | 예약 중심 테이블. order_id로 멱등성 보장. status: PENDING → COMPLETED / FAILED / CANCELLED |
| `payment` | booking_id, payment_type, amount, status, pg_transaction_id | 결제 수단별 레코드. 복합 결제 시 booking당 N개 생성. type: CREDIT_CARD / Y_PAY / Y_POINT |

---

## 핵심 설계

### 1. Redis Lua Script — 원자적 선착순 처리

단일 Lua Script로 아래 5단계를 원자적으로 실행해 Race Condition을 차단합니다.

```
입력: userId, promotionId, promotionRoomTypeId, orderId

1. sale_start:promotion:{id} vs Redis TIME → 판매 시작 전이면 NOT_STARTED
2. rate_limit:{userId} INCR (TTL 1초)     → 초당 3회 초과 시 RATE_LIMITED
3. idempotency:booking:{orderId} SET NX   → 중복 요청 시 ALREADY_PROCESSED
4. stock:promotionRoomType:{id} DECR      → 재고 0 이하 시 SOLD_OUT
5. 모두 통과 → SUCCESS
```

### 2. 결제 전략 패턴

```
PaymentProcessor (interface)
├── CreditCardPaymentProcessor   // 토스페이먼츠 PG 연동
├── YPayPaymentProcessor          // Y페이 PG 연동
└── YPointPaymentProcessor        // Y포인트 (PG 없음, DB 차감)
```

복합 결제 허용 조합:
- ✅ 신용카드 + Y포인트
- ✅ Y페이 + Y포인트
- ❌ 신용카드 + Y페이 (PG 중복 불가)

### 3. 장애 대응

| 장애 상황 | 대응 |
|-----------|------|
| Redis 장애 | Circuit Breaker → DB 비관적 락 Fallback + Bulkhead(동시 10개 제한) |
| PG 일시 오류 | Resilience4j Retry (최대 2회, 500ms 간격) |
| PG 결과 불분명 | `noRollbackFor = PgUncertainException` → Booking PENDING 커밋 유지 |
| PG CB OPEN | 결제 미발생 확실 → Redis 재고 복구 후 롤백 |
| PENDING 복구 | 배치 스캐너 + PG 웹훅으로 결제 결과 재확인 |

---

## 시퀀스 다이어그램

### 프로모션 예약 플로우 (정상)

<img width="1029" height="588" alt="image" src="https://github.com/user-attachments/assets/2e6e6eb1-5d6e-418d-8cf3-b16c94a1243d" />

### 재고 소진 / 결제 실패 플로우

<img width="1026" height="531" alt="image" src="https://github.com/user-attachments/assets/26246b47-b3a0-4142-8210-4fd972629799" />

---

## 실행 방법

### 사전 요구사항
- Docker Desktop 설치

### 1. 전체 기동 (MySQL + Redis + Spring App)

```bash
docker compose up --build -d
```

앱 시작 시 `DataInitializer`가 자동으로 테스트 데이터를 세팅합니다.

### 2. 초기 데이터 확인

```bash
docker compose logs app | grep -E "(userId|roomTypeId|promotionRoomTypeId|k6 유저)"
```

출력 예시:
```
테스트 계정 - userId: 1
roomTypeId: 1
promotionRoomTypeId: 1
k6 유저 범위 - userId: 3 ~ 502
```

### 3. k6 부하 테스트 (500 VU 동시 예약)

```bash
# k6 프로파일로 실행 (컨테이너 환경)
docker compose --profile k6 up

```

## API 목록

### 1. 주문서 발급 (Checkout)

```
GET /api/checkout/{roomTypeId}?userId={userId}&promotionRoomTypeId={promotionRoomTypeId}
```

| 파라미터 | 필수 | 설명 |
|----------|------|------|
| `roomTypeId` | O | 객실 타입 ID (path) |
| `userId` | O | 사용자 ID (query) |
| `promotionRoomTypeId` | X | 프로모션 상품 ID (없으면 일반 예약) |

**응답**
```json
{
  "result": {
    "orderId": "ORD-20260507-xxxx-xxxx",
    "accommodationName": "제주 초특가 호텔",
    "roomTypeName": "디럭스 더블",
    "originalAmount": 200000,
    "promotionAmount": 99000,
    "totalAmount": 99000,
    "checkInTime": "15:00",
    "checkOutTime": "11:00",
    "userName": "테스트유저",
    "availablePoint": 100000
  }
}
```

---

### 2. 예약 요청 (Booking)

```
POST /api/booking
Content-Type: application/json
```

**요청 바디**
```json
{
  "orderId": "ORD-20260507-xxxx-xxxx",
  "userId": 1,
  "roomTypeId": 1,
  "promotionRoomTypeId": 1,
  "totalAmount": 99000,
  "checkInDate": "2026-05-10",
  "checkOutDate": "2026-05-11",
  "pgTransactionId": "pg-txn-001",
  "paymentMethods": [
    { "type": "CREDIT_CARD", "amount": 79000 },
    { "type": "Y_POINT", "amount": 20000 }
  ]
}
```

| 필드 | 필수 | 설명 |
|------|------|------|
| `promotionRoomTypeId` | X | null이면 일반 예약 |
| `pgTransactionId` | X | Y_POINT 전액 결제 시 null 가능 |
| `paymentMethods.type` | O | `CREDIT_CARD` / `Y_PAY` / `Y_POINT` |

**응답**
```json
{
  "result": {
    "bookingId": 1,
    "orderId": "ORD-20260507-xxxx-xxxx",
    "totalAmount": 99000
  }
}
```

---

### 3. PG 웹훅 수신 (결제 결과 복구)

```
POST /api/v1/pg/webhook
Content-Type: application/json
```

**요청 바디**
```json
{
  "paymentKey": "pg-payment-key-001",
  "orderId": "ORD-20260507-xxxx-xxxx",
  "status": "DONE",
  "totalAmount": 99000
}
```

PENDING 상태 예약의 결제 결과를 PG로부터 수신해 COMPLETED 또는 FAILED로 업데이트합니다.

---

## 초기 테스트 데이터

앱 기동 시 자동 생성되는 데이터:

| 항목 | 값 |
|------|-----|
| 숙소 | 제주 초특가 호텔 |
| 객실 타입 | 디럭스 더블 (정가 200,000원) |
| 프로모션 가격 | 99,000원 |
| 프로모션 재고 | 10개 |
| 판매 기간 | 앱 기동 시점 ~ 30일 후 |
| 예약 가능일 | 오늘 ~ 7일 후 |
| 테스트 유저 포인트 | 100,000원 |
| k6 부하 테스트 유저 | 500명 (포인트 각 100,000원) |
