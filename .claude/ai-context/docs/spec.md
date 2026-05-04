# PRD: 초특가 숙소 선착순 예약 시스템

## 1. 개요

매일 00시에 오픈되는 초특가 숙소 상품(10개 한정) 선착순 예약 시스템.
2대 이상의 앱 서버 분산 환경, 인프라 증설 제한적 상황 가정.

**트래픽**: 평시 50 TPS / 프로모션(00시) 500~1,000 TPS (1~5분간)

---

## 2. API 설계

### 2.1 GET /api/checkout/{productId} (주문서 진입)

**응답**: 상품 정보(상품명, 가격, 입실/퇴실 시간, 잔여재고), 사용자 정보(가용 Y포인트), `orderId`

**처리 흐름**
1. DB에서 상품 정보 + 사용자 포인트 조회
2. `orderId` 생성 (형식: `ORD-yyyyMMdd-UUID`)
3. Redis에 주문서 캐시 저장 (key: `checkout:{orderId}`, value: `{productId, amount, userId}`, TTL: 10분)
4. 응답 반환

> **orderId 일원화**: 하나의 `orderId`가 아래 세 역할을 모두 수행
> 1. PG사 결제 요청 키
> 2. DB 주문 식별자 (`booking.order_id`)
> 3. Booking API 멱등성 키 (`X-Idempotency-Key` 헤더)

### 2.2 POST /api/booking (결제 및 예약 완료)

**요청 헤더**: `X-Idempotency-Key: ORD-20260504-xxxx-xxxx`

**요청 바디**
```json
{
  "orderId": "ORD-20260504-xxxx-xxxx",
  "userId": 1,
  "productId": 1,
  "totalAmount": 100000,
  "paymentMethods": [
    { "type": "CREDIT_CARD", "amount": 80000 },
    { "type": "Y_POINT", "amount": 20000 }
  ]
}
```

**처리 흐름**
1. **사전 금액 검증** — `checkout:{orderId}`의 원본 금액과 요청 `totalAmount` 일치 여부 비교 (Fail-Fast)
2. **Redis Lua Script (원자적 처리)** — 시간검증 → Rate Limit → 멱등성 → 재고차감을 단일 스크립트로 실행
3. **포인트 검증** — DB에서 유저의 실제 포인트 잔액이 요청한 포인트 사용 금액 이상인지 확인
4. **PG 승인** — 카드/Y페이 결제 금액에 대해 외부 PG 승인 호출 (orderId를 PG 주문번호로 사용)
5. **DB 영구 저장** — booking 테이블에 주문 생성, 포인트 차감 반영
6. **성공/실패 처리** — 성공 시 멱등성 키 상태를 COMPLETED(TTL 10분)로 변경 / 실패 시 재고 복구(INCR) + 멱등성 키 삭제(DEL)

---

## 3. 핵심 설계

### 3.1 orderId 일원화

| 역할 | 사용처 |
|------|--------|
| PG 결제 요청 키 | PG사 결제 승인 시 orderId로 전달 |
| DB 주문 식별자 | `booking.order_id` (UNIQUE) |
| 멱등성 키 | `X-Idempotency-Key` 헤더 + Redis `idempotency:booking:{orderId}` |

### 3.2 재고 정합성 + 공정성 + 멱등성 (단일 Lua Script)

- 시간검증 → Rate Limit → 멱등성 → 재고차감을 **단일 Lua Script**로 원자적 실행
- Redis 싱글 스레드 특성으로 "먼저 도달한 요청이 먼저 처리" = 공정성 보장
- Java ↔ Redis 왕복 최소화로 네트워크 지연에 의한 순서 역전 방지
- 500~1,000 TPS에서 Lua Script 성능 충분 (Redis 10만+ ops/sec)
- 사용자별 초당 3회 제한으로 매크로 방지
- Checkout에서 `orderId` 발급 → Booking에서 `X-Idempotency-Key`로 전송
- DB `booking.order_id` UNIQUE 제약으로 이중 삽입 영구 방지

**멱등성 키 TTL 전략**
| 상태 | TTL | 근거 |
|------|-----|------|
| PROCESSING | 30초 | PG사 통신 지연 등 최악의 타임아웃 고려. 이 시간 동안 동일 orderId 요청은 연타로 간주하여 차단 |
| COMPLETED | 10분 | 결제 성공 후 클라이언트 새로고침/뒤로가기 등 비정상 요청 방어. 10분 이후에는 DB UNIQUE 제약으로 영구 방지하므로 Redis 메모리 효율적 회수 |

### 3.4 결제 확장성 (전략 패턴)

```
PaymentProcessor (interface)
├── CreditCardPaymentProcessor   // 신용카드 (토스페이먼츠)
├── YPayPaymentProcessor          // Y페이
└── YPointPaymentProcessor        // Y포인트
```

- `PaymentProcessorRegistry`가 type → processor 매핑 관리
- 복합 결제: 신용카드+Y포인트 ✅ / Y페이+Y포인트 ✅ / 신용카드+Y페이 ❌
- 금액 위변조 방지: Redis 캐시 가격 vs 요청 금액 비교

### 3.5 장애 대응

**결제 실패**: PG 실패 → Redis 재고 복구 / 복합 결제 부분 실패 → 보상 트랜잭션 / 포인트 차감 후 PG 실패 → 포인트 환불

**Redis 장애 Fallback**: Resilience4j Circuit Breaker → DB 비관적 락(`SELECT FOR UPDATE`) 전환 / 멱등성은 DB UNIQUE 제약으로 보장

---

## 4. 데이터 모델

### 4.1 MySQL

#### 숙박 도메인

**accommodation (숙박 업체)**
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | 업체 ID |
| name | VARCHAR | 업체명 |
| address | VARCHAR | 주소 |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

**room_type (객실 타입)**
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | 객실타입 ID |
| accommodation_id | BIGINT FK | 업체 ID |
| name | VARCHAR | 객실명 (디럭스, 스위트 등) |
| price | DECIMAL(10,2) | 기본 가격 |
| capacity | INT | 수용 인원 |
| room_count | INT | 총 객실 수 |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

**room_availability (날짜별 객실 재고 — 핵심 재고 관리 테이블)**
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | ID |
| room_type_id | BIGINT FK | 객실타입 ID |
| date | DATE | 날짜 |
| available_count | INT | 예약 가능 수 |
| price | DECIMAL(10,2) | 해당 날짜 판매가 |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |
| UQ(room_type_id, date) | | 중복 방지 |

#### 프로모션 도메인

**promotion (프로모션 이벤트)**
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | 프로모션 ID |
| name | VARCHAR | 프로모션명 |
| sale_start_time | DATETIME | 판매 시작 시간 (00시) |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

**promotion_room_type (프로모션 적용 상품 매핑)**
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | ID |
| promotion_id | BIGINT FK | 프로모션 ID |
| room_type_id | BIGINT FK | 객실타입 ID |
| promotion_price | DECIMAL(10,2) | 프로모션 할인가 |
| stock | INT | 프로모션 재고 |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

#### 주문/결제 도메인

**user (사용자)**
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | 사용자 ID |
| name | VARCHAR | 예약자 이름 |
| phone | VARCHAR | 휴대폰 번호 |
| point_balance | INT | Y포인트 잔액 |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

**booking (예약)**
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | 주문 ID |
| order_id | VARCHAR UQ | `ORD-yyyyMMdd-UUID` |
| user_id | BIGINT FK | 사용자 ID |
| room_type_id | BIGINT FK | 객실타입 ID |
| promotion_room_type_id | BIGINT FK (nullable) | 프로모션 상품 ID (일반 예약이면 NULL) |
| check_in_date | DATE | 체크인 날짜 |
| check_out_date | DATE | 체크아웃 날짜 |
| status | ENUM | PENDING, COMPLETED, FAILED, CANCELLED |
| total_amount | DECIMAL(10,2) | 총 결제 금액 |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

**payment (결제)**
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | 결제 ID |
| booking_id | BIGINT FK | 주문 ID |
| payment_type | ENUM | CREDIT_CARD, Y_PAY, Y_POINT |
| amount | DECIMAL(10,2) | 결제 금액 |
| status | ENUM | SUCCESS, FAILED, CANCELLED |
| pg_payment_key | VARCHAR | PG 결제키 |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

### 4.2 Redis 키

| 키 | TTL | 설명 |
|----|-----|------|
| `stock:promotionRoomType:{id}` | - | 프로모션 상품 잔여 재고 |
| `sale_start:promotion:{id}` | - | 프로모션 판매 시작 시간 |
| `checkout:{orderId}` | 10분 | 주문서 캐시 (amount, userId) |
| `idempotency:booking:{orderId}` | PROCESSING: 30초 / COMPLETED: 10분 | 멱등성 결과 |
| `rate_limit:{userId}` | 1초 | 요청 제한 카운터 |

---

## 5. Lua Script (원자적 선착순 처리)

```
입력: userId, promotionId, promotionRoomTypeId, orderId
처리:
  1. sale_start:promotion:{promotionId} vs Redis TIME → NOT_STARTED
  2. rate_limit:{userId} INCR → 초과 시 RATE_LIMITED
  3. idempotency:booking:{orderId} SET NX → 이미 존재하면 ALREADY_PROCESSED
  4. stock:promotionRoomType:{id} DECR → 0 미만이면 INCR 복구 + 멱등성 키 삭제 후 SOLD_OUT
  5. 통과 → SUCCESS
```

---

## 6. 검증 계획

1. 단위 테스트: 서비스, 결제 프로세서별
2. 통합 테스트: Checkout → Booking 전체 플로우
3. 동시성 테스트: 멀티스레드 동시 예약 → 초과판매 없음 확인
4. 멱등성 테스트: 동일 orderId 중복 요청 → 단일 처리
5. 결제 실패 테스트: PG 실패 시 재고 복구
6. Redis 장애 테스트: DB Fallback 동작
