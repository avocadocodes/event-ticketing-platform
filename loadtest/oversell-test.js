import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID || 'EVT-001';
const BOOKINGS = parseInt(__ENV.BOOKINGS || '300');

const oversells = new Counter('oversell_violations');

export const options = {
    scenarios: {
        burst: {
            executor: 'shared-iterations',
            vus: 50,
            iterations: BOOKINGS,
            maxDuration: '60s',
        },
    },
    thresholds: {
        oversell_violations: ['count==0'],
    },
};

export default function () {
    const seats = Math.random() < 0.5 ? 1 : 2;
    const customerId = `CUST-${__VU}-${__ITER}`;

    const payload = JSON.stringify({
        eventId: EVENT_ID,
        customerId: customerId,
        seatCount: seats,
    });

    const res = http.post(`${BASE_URL}/bookings`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });

    check(res, {
        'booking accepted (201 or 409)': (r) => r.status === 201 || r.status === 409 || r.status === 200,
    });
}

export function handleSummary(data) {
    // Poll availability after all iterations complete
    const availRes = http.get(`${BASE_URL}/inventory/${EVENT_ID}/availability`);
    if (availRes.status === 200) {
        const body = JSON.parse(availRes.body);
        const totalSeats = body.totalSeats;
        const availableSeats = body.availableSeats;
        const usedOrHeld = totalSeats - availableSeats;

        console.log(`Total seats: ${totalSeats}`);
        console.log(`Available (excluding holds): ${availableSeats}`);
        console.log(`Used or held: ${usedOrHeld}`);

        if (usedOrHeld > totalSeats) {
            oversells.add(1);
            console.error(`OVERSELL DETECTED: used/held (${usedOrHeld}) > total (${totalSeats})`);
        } else {
            console.log(`Invariant holds: sold+holds (${usedOrHeld}) <= total (${totalSeats})`);
        }
    }

    return {
        stdout: JSON.stringify(data, null, 2),
    };
}
