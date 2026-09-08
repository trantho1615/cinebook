import http from 'k6/http';
import { check } from 'k6';

// Do THONG LUONG: day den bao nhieu request/giay thi he thong bat dau gay, va gay o dau.
//
// Khac han flash-sale.js, va su khac nhau la co y:
//
//   flash-sale.js  dung constant-vus VOI sleep(). No tra loi cau "khi tranh chap thi ai
//                  thang va mat bao lau". Con so 26,3 req/s cua no la do CHINH KICH BAN
//                  ap dat (50 VU nghi 1-2 giay moi vong), khong phai gioi han he thong.
//
//   throughput.js  dung constant-arrival-rate, KHONG sleep. k6 giu dung nhip den bat ke
//                  server tra loi nhanh hay cham. Do la cach duy nhat de tim diem gay:
//                  voi constant-vus, server cham lai thi tai cung tu dong giam theo, va
//                  ta khong bao gio thay he thong that su duoi o dau.
//
// Chay:
//   MSYS_NO_PATHCONV=1 docker run --rm -i --add-host=host.docker.internal:host-gateway \
//     -v "$PWD/load-test:/scripts" grafana/k6:1.5.0 run /scripts/throughput.js

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8080';

// Suat chieu lay tu du lieu do tai (seed-large.sql), moi suat co 60 dong trong seat_hold
// tren bang 12 trieu dong. Dung suat cua profile demo thi bang to bao nhieu cung khong
// quan trong: truy van chi cham vai dong va ta do nham mot he thong nho.
//
// Sinh file nay:
//   docker exec cinebook-postgres psql -U cinebook -d cinebook -tAc \
//     "SELECT json_agg(id)::text FROM (SELECT id FROM seed_ref_showtimes ORDER BY rn LIMIT 5000) t;" \
//     > load-test/showtime-ids.json
const SUAT_CHIEU = JSON.parse(open('./showtime-ids.json'));

// Cac bac tai. Moi bac la mot scenario rieng chay noi tiep nhau, thay vi mot
// ramping-arrival-rate lien tuc: nhu vay k6 cho ra so lieu TACH BACH cho tung bac, doc
// duoc ngay ma khong phai tu cat bieu do.
// Ghi de bang bien moi truong de do mot bac duy nhat, vi du khi quet cac gia tri pool:
//   -e BUOC=4000
const BUOC = (__ENV.BUOC || '100,250,500,1000,2000,4000').split(',').map(Number);
const GIAY_MOI_BUOC = 30;
const GIAY_NGHI = 5;

function scenarios() {
    const s = {};
    BUOC.forEach((rate, i) => {
        s['buoc_' + rate] = {
            executor: 'constant-arrival-rate',
            rate: rate,
            timeUnit: '1s',
            duration: GIAY_MOI_BUOC + 's',
            startTime: i * (GIAY_MOI_BUOC + GIAY_NGHI) + 's',
            // preAllocatedVUs phai du cho bac nay o do tre BINH THUONG; maxVUs du cho luc
            // no bat dau cham. Thieu VU thi k6 khong sinh du tai va bao dropped_iterations
            // — luc do moi ket luan ve "he thong chi chiu duoc x RPS" deu sai.
            preAllocatedVUs: Math.max(50, Math.ceil(rate / 10)),
            maxVUs: Math.max(500, rate),
            exec: 'seatMap',
            tags: { buoc: String(rate) },
        };
    });
    return s;
}

// Nguong dat sao cho LUON DAT. Muc dich khong phai pass/fail ma la ep k6 in ra so lieu
// cua tung bac trong bang tong ket — k6 chi hien sub-metric theo tag neu tag do co mat
// trong thresholds.
function thresholds() {
    const t = {};
    BUOC.forEach((rate) => {
        t['http_req_duration{buoc:' + rate + '}'] = ['p(95)>=0'];
        t['http_req_failed{buoc:' + rate + '}'] = ['rate>=0'];
        t['http_reqs{buoc:' + rate + '}'] = ['count>=0'];
    });
    return t;
}

export const options = {
    scenarios: scenarios(),
    thresholds: thresholds(),
    // BAT BUOC phai khai o day. Mac dinh k6 chi tinh avg/min/med/max/p(90)/p(95) — doc
    // values['p(99)'] khi chua khai thi duoc undefined, va handleSummary nem
    // "Cannot read property 'toFixed' of undefined". Lan chay dau da dinh dung loi do:
    // k6 bo qua handleSummary va chi in bang mac dinh.
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    // Khong dung ket qua ngay khi mot nguong hong: ta muon di het cac bac de nhin thay
    // duong cong, ke ca phan da gay.
    noConnectionReuse: false,
    discardResponseBodies: false,
};

export function seatMap() {
    const id = SUAT_CHIEU[Math.floor(Math.random() * SUAT_CHIEU.length)];
    const res = http.get(`${BASE}/showtimes/${id}/seats`, {
        tags: { name: 'seatmap' },
    });
    check(res, { 'seat map 200': (r) => r.status === 200 });
}

// Doc mot thong ke cua Trend ma khong sap neu k6 khong tinh no.
function stat(metric, ten) {
    if (!metric || !metric.values || metric.values[ten] === undefined) return null;
    return metric.values[ten];
}

function o(v, rong) {
    return (v === null ? '-' : v.toFixed(1)).padStart(rong);
}

export function handleSummary(data) {
    const d = [];
    d.push('');
    d.push('=== Ket qua theo tung bac (' + GIAY_MOI_BUOC + ' giay moi bac) ===');
    d.push('');
    d.push('   muc tieu |  dat duoc |   %dat |    med |    p95 |    p99 |  loi % |  bo lo');
    d.push('   ---------|-----------|--------|--------|--------|--------|--------|-------');

    for (const rate of BUOC) {
        const dur = data.metrics['http_req_duration{buoc:' + rate + '}'];
        const fail = data.metrics['http_req_failed{buoc:' + rate + '}'];
        const cnt = data.metrics['http_reqs{buoc:' + rate + '}'];
        if (!cnt) continue;

        const mongDoi = rate * GIAY_MOI_BUOC;
        const thuc = cnt.values.count / GIAY_MOI_BUOC;
        const boLo = mongDoi - cnt.values.count;

        d.push(
            '   ' + String(rate).padStart(8) +
            ' | ' + thuc.toFixed(0).padStart(9) +
            ' | ' + ((thuc / rate) * 100).toFixed(0).padStart(5) + '%' +
            ' | ' + o(stat(dur, 'med'), 6) +
            ' | ' + o(stat(dur, 'p(95)'), 6) +
            ' | ' + o(stat(dur, 'p(99)'), 6) +
            ' | ' + (fail ? (fail.values.rate * 100).toFixed(2) : '-').padStart(6) +
            ' | ' + String(boLo > 0 ? boLo : 0).padStart(6)
        );
    }

    const bo = data.metrics.dropped_iterations;
    const tongBoLo = bo ? bo.values.count : 0;
    d.push('');
    d.push('   dropped_iterations: ' + tongBoLo);
    d.push('');
    d.push('   Cot "bo lo" khac 0 nghia la k6 khong sinh du tai o bac do — thuong vi moi');
    d.push('   iteration keo dai qua lau nen cham tran maxVUs. O nhung bac do, con so RPS');
    d.push('   "dat duoc" van la thong luong THAT cua he thong, nhung KHONG duoc doc no');
    d.push('   nhu "he thong chi chiu duoc chung nay khi bi de dung muc tieu".');
    d.push('');

    return {
        stdout: d.join('\n') + '\n',
        '/scripts/throughput-summary.json': JSON.stringify(data, null, 2),
    };
}
