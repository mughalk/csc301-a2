/**
 * k6 workload Generator for CSC301 A2 Component 2
 *
 * Usage:
 *   k6 run load-gen.js
 *   k6 run -e TARGET=http://142.1.46.113:14002 -e VUS=500 -e DURATION=5s load-gen.js
 *
 * ENV vars:
 *   TARGET   — base URL of OrderService  (default: http://127.0.0.1:14002)
 *   VUS      — number of virtual users   (default: 100)
 *   DURATION — test duration             (default: 1s)
 */

import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const TARGET   = __ENV.TARGET   || 'http://127.0.0.1:14002';
const VUS      = parseInt(__ENV.VUS      || '100');
const DURATION = __ENV.DURATION || '1s';

export const options = {
    vus:      VUS,
    duration: DURATION,
    thresholds: {
        http_req_failed:   ['rate<0.05'],
        http_req_duration: ['p(95)<1000'],
    },
};

const HEADERS = { 'Content-Type': 'application/json' };

// Seed data — created in setup() so GETs have real rows to hit
const SEED_USER_IDS    = [1, 2, 3, 4, 5];
const SEED_PRODUCT_IDS = [1, 2, 3, 4, 5];

// setup() — runs once before VUs start
export function setup() {
    for (const id of SEED_USER_IDS) {
        http.post(`${TARGET}/user`, JSON.stringify({
            command:  'create',
            id:       id,
            username: `seeduser${id}`,
            email:    `seeduser${id}@test.com`,
            password: `seedpass${id}`,
        }), { headers: HEADERS });
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
        }), { headers: HEADERS });
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
