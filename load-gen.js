/**
 * k6 workload Generator for CSC301 A2 Component 2
 *
 * Usage:
 *   k6 run load-gen.js
 *   k6 run -e TARGET=http://<vm-ip>:14002 -e VUS=20 -e DURATION=30s load-gen.js
 *
 * ENV vars:
 *   TARGET   — base URL of OrderService  (default: http://127.0.0.1:14002)
 *   VUS      — number of virtual users   (default: 20)
 *   DURATION — test duration             (default: 30s)
 *
 * TPS = look at `iterations/s` in summary, NOT `http_reqs/s`.
 * VU sizing: VUs ≈ target_TPS × avg_latency_seconds
 */

import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const TARGET   = __ENV.TARGET   || 'http://127.0.0.1:14002';
const VUS      = parseInt(__ENV.VUS      || '20');
const DURATION = __ENV.DURATION || '30s';

// Force HTTP/1.1 keep-alive so k6 reuses TCP connections instead of
// opening a new socket per request (prevents ephemeral port exhaustion).
export const options = {
    vus:      VUS,
    duration: DURATION,
    noConnectionReuse: false,  // keep-alive ON; prevents ephemeral port exhaustion
    thresholds: {
        http_req_failed:   ['rate<0.05'],
        http_req_duration: ['p(95)<2000'],
    },
};

const HEADERS = { 'Content-Type': 'application/json' };

// Seed data — created in setup() so GETs have real rows to hit
const SEED_USER_IDS    = [1, 2, 3, 4, 5];
const SEED_PRODUCT_IDS = [1, 2, 3, 4, 5];

const SETUP_PARAMS = { headers: HEADERS, timeout: '5s' };

// setup() — runs once before VUs start
export function setup() {
    // Fire the first-request gate with "restart" so it does NOT wipe the DB.
    // Without this, whatever VU request arrives first after a crash/restart
    // triggers wipeDatabases(), destroying all seed data mid-test.
    http.post(`${TARGET}/order`, JSON.stringify({ command: 'restart' }), SETUP_PARAMS);

    for (const id of SEED_USER_IDS) {
        http.post(`${TARGET}/user`, JSON.stringify({
            command:  'create',
            id:       id,
            username: `seeduser${id}`,
            email:    `seeduser${id}@test.com`,
            password: `seedpass${id}`,
        }), SETUP_PARAMS);
    }

    for (const id of SEED_PRODUCT_IDS) {
        http.post(`${TARGET}/product`, JSON.stringify({
            command:     'create',
            id:          id,
            name:        `SeedProduct${id}`,
            productname: `SeedProduct${id}`,
            description: `Seed product ${id}`,
            price:       id * 9.99,
            quantity:    100000,
        }), SETUP_PARAMS);
        // Always reset quantity in case product already existed with depleted stock
        http.post(`${TARGET}/product`, JSON.stringify({
            command:  'update',
            id:       id,
            quantity: 100000,
        }), SETUP_PARAMS);
    }
}

// Helpers
function pick(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

function randInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

// The kinds of requests that we will be sending; each VU iteration picks one of 6 request types at random,
// all equally likely (1/6 chance each and all must have expected response codes)
const TYPES = [
    'GET_USER',
    'GET_PRODUCT',
    'GET_ORDERS',
    'POST_ORDER',
    'POST_USER',
    'POST_PRODUCT',
];

export default function () {
    const iter = exec.scenario.iterationInTest;
    const type = pick(TYPES);

    switch (type) {

        case 'GET_USER': {
            const id  = pick(SEED_USER_IDS);
            const res = http.get(`${TARGET}/user/${id}`);
            check(res, { 'get user 200/404': (r) => r.status === 200 || r.status === 404 });
            break;
        }

        case 'GET_PRODUCT': {
            const id  = pick(SEED_PRODUCT_IDS);
            const res = http.get(`${TARGET}/product/${id}`);
            check(res, { 'get product 200/404': (r) => r.status === 200 || r.status === 404 });
            break;
        }

        case 'GET_ORDERS': {
            const id  = pick(SEED_USER_IDS);
            const res = http.get(`${TARGET}/user/purchased/${id}`);
            check(res, { 'get orders 200/404': (r) => r.status === 200 || r.status === 404 });
            break;
        }

        case 'POST_ORDER': {
            const res = http.post(`${TARGET}/order`, JSON.stringify({
                command:    'place order',
                product_id: pick(SEED_PRODUCT_IDS),
                user_id:    pick(SEED_USER_IDS),
                quantity:   randInt(1, 5),
            }), { headers: HEADERS });
            check(res, { 'place order 200/201': (r) => r.status === 200 || r.status === 201 });
            break;
        }

        case 'POST_USER': {
            const id  = 1000 + iter;
            const res = http.post(`${TARGET}/user`, JSON.stringify({
                command:  'create',
                id:       id,
                username: `user_${iter}`,
                email:    `user_${iter}@test.com`,
                password: 'testpass',
            }), { headers: HEADERS });
            // 409 is fine b/c another VU may have created this ID already
            check(res, { 'create user 200/201/409': (r) => r.status === 200 || r.status === 201 || r.status === 409 });
            break;
        }

        case 'POST_PRODUCT': {
            const id  = 1000 + iter;
            const res = http.post(`${TARGET}/product`, JSON.stringify({
                command:     'create',
                id:          id,
                name:        `Product_${iter}`,
                productname: `Product_${iter}`,
                description: 'load-test product',
                price:       randInt(1, 100),
                quantity:    10000,
            }), { headers: HEADERS });
            check(res, { 'create product 200/201/409': (r) => r.status === 200 || r.status === 201 || r.status === 409 });
            break;
        }
    }
}
