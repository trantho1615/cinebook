import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// Kich ban flash sale: rat nhieu nguoi mo CUNG MOT suat chieu, phan lon chi xem so do ghe,
// mot nhom gianh nhau CUNG MOT VUNG GHE HEP, mot nhom nho thanh toan.
//
// Vung ghe hep la co y. Trai deu 96 ghe thi gan nhu khong ai dung ai, va con so thu duoc
// se dep mot cach vo nghia — trong khi chinh cho gianh nhau moi la thu Milestone 4 dung len
// de chiu, va la thu dang do.

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const MAT_KHAU = 'MatKhauRatManh123';
const SO_NGUOI_DUNG = 20;

const luotXungDot = new Counter('cinebook_xung_dot');
const luotGiuThanhCong = new Counter('cinebook_giu_thanh_cong');
const thoiGianGiuGhe = new Trend('cinebook_thoi_gian_giu_ghe', true);

// 409 SEATS_UNAVAILABLE KHONG phai loi.
//
// Trong kich ban nay no la ket qua nghiep vu dung dan: co nguoi nhanh tay hon. Danh dau no
// la loi thi http_req_failed tang vot va moi ket luan sau do deu sai.
//
// PHAI dat o day chu khong phai trong options: lan chay dau tien tui de
// "responseCallback" trong options va no khong co tac dung nao ca — bai do bao
// http_req_failed = 26.97%, dung bang so luot 409. Chinh cho de tu lua minh nhat cua ca
// bai do tai, va no da lua duoc mot lan.
http.setResponseCallback(http.expectedStatuses(200, 201, 204, 409));

export const options = {
    scenarios: {
        nguoi_xem: {
            executor: 'constant-vus',
            vus: 40,
            duration: '60s',
            exec: 'nguoiXem',
        },
        nguoi_gianh_ghe: {
            executor: 'constant-vus',
            vus: 8,
            duration: '60s',
            exec: 'nguoiGianhGhe',
        },
        nguoi_mua: {
            executor: 'constant-vus',
            vus: 2,
            duration: '60s',
            exec: 'nguoiMua',
        },
    },

    thresholds: {
        'http_req_duration{name:seatmap}': ['p(95)<300'],
        'http_req_duration{name:hold}': ['p(95)<800'],
        'http_req_failed': ['rate<0.01'],
    },
};

export function setup() {
    const nguoiDung = [];
    for (let i = 0; i < SO_NGUOI_DUNG; i++) {
        const email = `loadtest${i}@example.com`;
        // 409 nghia la lan chay truoc da tao — bo qua va dang nhap binh thuong.
        http.post(`${BASE}/auth/register`, JSON.stringify({
            email, password: MAT_KHAU, fullName: `Load Test ${i}`, phone: '0900000000',
        }), { headers: { 'Content-Type': 'application/json' } });

        const dangNhap = http.post(`${BASE}/auth/login`, JSON.stringify({
            email, password: MAT_KHAU,
        }), { headers: { 'Content-Type': 'application/json' } });

        nguoiDung.push(dangNhap.json('accessToken'));
    }

    const suatChieu = http.get(`${BASE}/showtimes?from=${new Date().toISOString()}`).json();
    if (!suatChieu || suatChieu.length === 0) {
        throw new Error('Khong co suat chieu nao trong tuong lai — chay api voi profile demo');
    }
    const showtimeId = suatChieu[0].showtimeId;

    const ghe = http.get(`${BASE}/showtimes/${showtimeId}/seats`).json();
    // Vung hep: hai hang giua. Day la cho moi nguoi deu muon ngoi, va la cho ho gianh nhau.
    const vungHep = ghe.filter((g) => g.rowLabel === 'D' || g.rowLabel === 'E');

    return { nguoiDung, showtimeId, vungHep: vungHep.map((g) => g.seatId) };
}

function token(data) {
    return data.nguoiDung[__VU % data.nguoiDung.length];
}

function headers(data) {
    return {
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token(data)}`,
        },
    };
}

export function nguoiXem(data) {
    const res = http.get(`${BASE}/showtimes/${data.showtimeId}/seats`, {
        tags: { name: 'seatmap' },
    });
    check(res, { 'seatmap 200': (r) => r.status === 200 });
    sleep(2);
}

export function nguoiGianhGhe(data) {
    const bookingId = giuGhe(data);
    if (bookingId) {
        // Nguoi gianh duoc ghe nhung khong mua: nha ra de con ghe cho vong sau. Neu khong,
        // sau vai chuc giay moi ghe trong vung deu bi giu va bai do chi con do duong 409.
        sleep(1);
        http.del(`${BASE}/bookings/${bookingId}`, null, {
            ...headers(data),
            tags: { name: 'cancel' },
        });
    }
    sleep(1);
}

export function nguoiMua(data) {
    const bookingId = giuGhe(data);
    if (!bookingId) {
        sleep(1);
        return;
    }

    const thanhToan = http.post(`${BASE}/bookings/${bookingId}/payments`, '{}', {
        ...headers(data),
        tags: { name: 'payment' },
    });
    check(thanhToan, { 'payment 201': (r) => r.status === 201 });
    sleep(2);
}

function giuGhe(data) {
    const gheChon = chonHaiGheNgauNhien(data.vungHep);

    const res = http.post(
        `${BASE}/showtimes/${data.showtimeId}/holds`,
        JSON.stringify({ seatIds: gheChon }),
        { ...headers(data), tags: { name: 'hold' } },
    );

    thoiGianGiuGhe.add(res.timings.duration);

    if (res.status === 201) {
        luotGiuThanhCong.add(1);
        return res.json('bookingId');
    }
    if (res.status === 409) {
        luotXungDot.add(1);
        return null;
    }
    check(res, { 'hold khong loi la': (r) => r.status === 201 || r.status === 409 });
    return null;
}

function chonHaiGheNgauNhien(danhSach) {
    const i = Math.floor(Math.random() * danhSach.length);
    const j = (i + 1) % danhSach.length;
    return [danhSach[i], danhSach[j]];
}
