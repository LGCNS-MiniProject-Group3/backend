import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const TARGET_VUS = Number(__ENV.VUS || 100);

export const options = {
    scenarios: {
        login_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: Math.max(1, Math.ceil(TARGET_VUS * 0.1)) },
                { duration: '1m', target: TARGET_VUS },
                { duration: '30s', target: 0 },
            ],
        },
    },
    thresholds: {
        'http_req_duration{endpoint:login}': ['p(95)<500'],
        'http_req_failed{endpoint:login}': ['rate<0.01'],
    },
};

export default function () {
    const response = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({
            email: 'test@example.com',
            password: 'password',
        }),
        {
            headers: { 'Content-Type': 'application/json' },
            tags: { endpoint: 'login' },
        },
    );

    check(response, {
        '로그인 200': (r) => r.status === 200,
        'Access 토큰 발급': (r) => Boolean(r.headers['Authorization']),
        'Refresh 토큰 발급': (r) => Boolean(r.headers['Refresh-Token']),
    });

    sleep(0.1);
}
