import { get, post, token } from './api.js';

const app = document.getElementById('app');
const bookingId = new URLSearchParams(location.search).get('bookingId');

let nhipDemNguoc = null;

if (!token()) {
    location.href = '/index.html';
} else if (!bookingId) {
    app.innerHTML = '<p class="loi">Thieu bookingId</p>';
} else {
    hienDonHang();
}

async function hienDonHang() {
    const don = await get(`/bookings/${bookingId}`);

    if (don.status === 'CONFIRMED') {
        hienVe(don);
        return;
    }
    if (don.status !== 'PENDING') {
        app.innerHTML = `
            <section class="the">
                <h2>Don ${don.code}</h2>
                <p class="loi">Don o trang thai ${don.status}, khong thanh toan duoc nua.</p>
                <p><a href="/index.html">Ve trang chu</a></p>
            </section>`;
        return;
    }

    app.innerHTML = `
        <section class="the">
            <h2>${don.movieTitle}</h2>
            <p class="trong">${don.cinemaName} · ${gioDiaPhuong(don.startAt)}</p>
            <p>Ghe: <strong>${don.seats.join(', ')}</strong></p>
            <p>Ma don: <strong>${don.code}</strong></p>
            <p>Tong tien: <strong>${don.totalAmount.toLocaleString('vi-VN')} d</strong></p>
            <p>Ghe duoc giu them <strong class="dem-nguoc" id="dem-nguoc">--:--</strong></p>
            <div id="loi"></div>
            <p><button id="nut-thanh-toan">Thanh toan</button></p>
        </section>
        <div id="cong-thanh-toan"></div>`;

    demNguoc(new Date(don.holdExpiresAt).getTime());
    document.getElementById('nut-thanh-toan').addEventListener('click', khoiTaoThanhToan);
}

async function khoiTaoThanhToan() {
    const nut = document.getElementById('nut-thanh-toan');
    nut.disabled = true;
    document.getElementById('loi').innerHTML = '';

    try {
        const giaoDich = await post(`/bookings/${bookingId}/payments`);
        hienCongThanhToan(giaoDich);
    } catch (loi) {
        document.getElementById('loi').innerHTML = `<p class="loi">${loi.message}</p>`;
        nut.disabled = false;
    }
}

function hienCongThanhToan(giaoDich) {
    document.getElementById('nut-thanh-toan').disabled = true;

    // Mot cong that se dua nguoi dung sang trang cua ho. Hien link de nhin thay dieu do,
    // roi hai nut giai lap ket qua — vi trinh duyet KHONG the tu goi webhook: webhook doi
    // chu ky HMAC, ma bi mat ky thi khong duoc phep co mat trong JavaScript.
    document.getElementById('cong-thanh-toan').innerHTML = `
        <section class="the">
            <h2>Cong thanh toan</h2>
            <p class="trong">Cong that se chuyen huong toi:</p>
            <p class="trong"><small>${giaoDich.redirectUrl}</small></p>
            <p>So tien: <strong>${giaoDich.amount.toLocaleString('vi-VN')} d</strong></p>
            <p>
                <button id="nut-thanh-cong">Gia lap thanh cong</button>
                <button class="phu" id="nut-that-bai">Gia lap that bai</button>
            </p>
            <div id="loi-cong"></div>
        </section>`;

    document.getElementById('nut-thanh-cong')
        .addEventListener('click', () => giaiLap(giaoDich.paymentId, 'succeed'));
    document.getElementById('nut-that-bai')
        .addEventListener('click', () => giaiLap(giaoDich.paymentId, 'fail'));
}

async function giaiLap(paymentId, ketQua) {
    document.querySelectorAll('#cong-thanh-toan button').forEach((b) => {
        b.disabled = true;
    });

    try {
        await post(`/demo/payments/${paymentId}/${ketQua}`);
        await hienDonHang();
    } catch (loi) {
        // Chi nhac toi profile demo khi dung la endpoint khong ton tai; con lai thi hien
        // dung thong bao that, dung doan mo ho.
        const goiY = loi.status === 404 || loi.status === 401
            ? 'Cong gia lap chi co o profile demo. '
            : '';
        document.getElementById('loi-cong').innerHTML = `<p class="loi">${goiY}${loi.message}</p>`;
    }
}

function hienVe(don) {
    dungDemNguoc();
    app.innerHTML = `
        <section class="the">
            <h2>Dat ve thanh cong</h2>
            <p>Ma don: <strong>${don.code}</strong></p>
            <p>${don.movieTitle} — ${don.cinemaName}</p>
            <p>${gioDiaPhuong(don.startAt)}</p>
            <p>Ghe: <strong>${don.seats.join(', ')}</strong></p>
            <p>Da thanh toan: <strong>${don.totalAmount.toLocaleString('vi-VN')} d</strong></p>
            <p><a href="/index.html">Dat them ve khac</a></p>
        </section>`;
}

function gioDiaPhuong(thoiDiem) {
    return new Date(thoiDiem).toLocaleString('vi-VN', {
        weekday: 'short', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
    });
}

function demNguoc(hetHanLuc) {
    dungDemNguoc();
    const o = document.getElementById('dem-nguoc');

    nhipDemNguoc = setInterval(() => {
        const conLai = Math.max(0, Math.round((hetHanLuc - Date.now()) / 1000));
        o.textContent = `${String(Math.floor(conLai / 60)).padStart(2, '0')}:`
            + `${String(conLai % 60).padStart(2, '0')}`;
        o.classList.toggle('gap', conLai <= 60);

        if (conLai === 0) {
            dungDemNguoc();
            // Het gio giu ghe. Doc lai don de hien dung trang thai that thay vi de nguoi
            // dung bam Thanh toan roi an mot loi 410.
            hienDonHang();
        }
    }, 1000);
}

function dungDemNguoc() {
    if (nhipDemNguoc) {
        clearInterval(nhipDemNguoc);
        nhipDemNguoc = null;
    }
}
