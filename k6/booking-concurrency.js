import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL      = __ENV.BASE_URL      || 'http://localhost:8080';
const BASE_USER_ID  = parseInt(__ENV.BASE_USER_ID || '3'); // k6유저0의 userId (앱 로그 확인)

const bookingSuccess = new Counter('booking_success');
const bookingFail    = new Counter('booking_fail');

export const options = {
  scenarios: {
    concurrent_booking: {
      executor: 'shared-iterations',
      vus: 500,
      iterations: 500,
      maxDuration: '3m',
    },
  },
};

export default function () {
  const userId = BASE_USER_ID + (__VU - 1); // VU1→userId3, VU300→userId302

  // 1. Checkout → orderId 발급
  const checkoutRes = http.get(
    `${BASE_URL}/api/checkout/1?userId=${userId}&promotionRoomTypeId=1`
  );
  check(checkoutRes, { 'checkout 200': (r) => r.status === 200 });

  if (checkoutRes.status !== 200) {
    bookingFail.add(1);
    console.warn(`[VU${__VU}] checkout 실패: ${checkoutRes.status} - ${checkoutRes.body}`);
    return;
  }

  const orderId     = checkoutRes.json('result.orderId');
  const totalAmount = checkoutRes.json('result.totalAmount');

  // 2. Booking
  const bookingRes = http.post(
    `${BASE_URL}/api/booking`,
    JSON.stringify({
      orderId:             orderId,
      userId:              userId,
      roomTypeId:          1,
      promotionRoomTypeId: 1,
      totalAmount:         totalAmount,
      checkInDate:         '2026-05-10',
      checkOutDate:        '2026-05-11',
      pgTransactionId:     `pg-k6-vu${__VU}`,
      paymentMethods:      [{ type: 'CREDIT_CARD', amount: totalAmount }],
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  const booked = check(bookingRes, { 'booking 200': (r) => r.status === 200 });

  if (booked) {
    bookingSuccess.add(1);
    console.log(`[VU${__VU}] ✅ 성공 userId=${userId} orderId=${orderId}`);
  } else {
    bookingFail.add(1);
    const reason = bookingRes.json('message') || bookingRes.status;
    console.log(`[VU${__VU}] ❌ 실패 userId=${userId} reason=${reason}`);
  }

  sleep(3);
}
