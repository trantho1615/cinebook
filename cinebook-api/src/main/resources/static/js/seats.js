import { get, post, token } from './api.js';
import { ketNoi } from './stomp.js';

const app = document.getElementById('app');
const showtimeId = new URLSearchParams(location.search).get('showtimeId');

/** Ghe dang chon nhung chua giu. */
const dangChon = new Set();
/** Ghe CHINH MINH dang giu — de phan biet voi ghe nguoi khac giu, ca hai deu la HELD. */
const daGiu = new Set();
let danhSachGhe = [];

if (!token()) {
    location.href = '/index.html';
} else if (!showtimeId) {
    app.innerHTML = '<p class="loi">Thieu showtimeId</p>';
} else {
    khoiDong();
}

async function khoiDong() {
    const [suat, ghe] = await Promise.all([
        get(`/showtimes/${showtimeId}`),
        get(`/showtimes/${showtimeId}/seats`),
    ]);
    danhSachGhe = ghe;

    document.getElementById('nguoi-dung').textContent = new Date(suat.startAt)
        .toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

    app.innerHTML = `
        <h2>${suat.movieTitle}</h2>
        <p class="trong">${suat.cinemaName} · ${suat.roomName}</p>
        <div class="man-hinh">MAN HINH</div>
        <div id="so-do"></div>
        <div class="chu-thich">
            <span class="k-trong">Trong</span>
            <span class="k-nguoi-khac">Nguoi khac giu</span>
            <span class="k-da-ban">Da ban</span>
            <span class="k-cua-toi">Ban chon</span>
        </div>
        <div id="loi"></div>
        <div class="thanh-hanh-dong" id="thanh-hanh-dong">
            <span id="tom-tat">Chua chon ghe nao</span>
            <button id="nut-giu" disabled>Giu ghe</button>
        </div>`;

    document.getElementById('nut-giu').addEventListener('click', giuGhe);
    veSoDo();
    ngheRealtime();
}

function veSoDo(nhanVuaDoi = []) {
    const theoHang = new Map();
    for (const ghe of danhSachGhe) {
        if (!theoHang.has(ghe.rowLabel)) {
            theoHang.set(ghe.rowLabel, []);
        }
        theoHang.get(ghe.rowLabel).push(ghe);
    }

    document.getElementById('so-do').innerHTML = [...theoHang.entries()]
        .map(([hang, gheTrongHang]) => `
            <div class="hang">
                <span class="ten-hang">${hang}</span>
                ${gheTrongHang.map((ghe) => veGhe(ghe, nhanVuaDoi)).join('')}
            </div>`)
        .join('');

    document.querySelectorAll('.ghe[data-seat-id]').forEach((nut) => {
        nut.addEventListener('click', () => doiChon(nut.dataset.seatId));
    });
}

function veGhe(ghe, nhanVuaDoi) {
    const lop = ['ghe'];
    let chonDuoc = false;

    if (daGiu.has(ghe.seatId)) {
        // Ghe cua chinh minh: van la HELD trong DB nhung phai khac mau ghe nguoi khac giu.
        lop.push('cua-toi');
    } else if (ghe.status === 'BOOKED') {
        lop.push('da-ban');
    } else if (ghe.status === 'HELD') {
        lop.push('nguoi-khac-giu');
    } else if (dangChon.has(ghe.seatId)) {
        lop.push('cua-toi');
        chonDuoc = true;
    } else {
        chonDuoc = true;
    }

    if (nhanVuaDoi.includes(ghe.label)) {
        lop.push('vua-doi');
    }

    return `<button class="${lop.join(' ')}" ${chonDuoc ? `data-seat-id="${ghe.seatId}"` : 'disabled'}
                    title="${ghe.label} — ${ghe.price.toLocaleString('vi-VN')} d">${ghe.seatNumber}</button>`;
}

function doiChon(seatId) {
    if (dangChon.has(seatId)) {
        dangChon.delete(seatId);
    } else {
        dangChon.add(seatId);
    }
    veSoDo();
    capNhatThanhHanhDong();
}

function capNhatThanhHanhDong() {
    const oTomTat = document.getElementById('tom-tat');
    if (!oTomTat) {
        // Da giu ghe xong: thanh hanh dong doi thanh dong ho dem nguoc, khong con gi de sua.
        return;
    }

    const daChon = danhSachGhe.filter((ghe) => dangChon.has(ghe.seatId));
    const tong = daChon.reduce((cong, ghe) => cong + ghe.price, 0);

    oTomTat.textContent = daChon.length === 0
        ? 'Chua chon ghe nao'
        : `${daChon.map((ghe) => ghe.label).join(', ')} — ${tong.toLocaleString('vi-VN')} d`;
    document.getElementById('nut-giu').disabled = daChon.length === 0;
}

async function giuGhe() {
    const oLoi = document.getElementById('loi');
    oLoi.innerHTML = '';
    document.getElementById('nut-giu').disabled = true;

    try {
        const ketQua = await post(`/showtimes/${showtimeId}/holds`, { seatIds: [...dangChon] });
        dangChon.forEach((seatId) => daGiu.add(seatId));
        dangChon.clear();
        hienDaGiu(ketQua);
        veSoDo(ketQua.seats);
    } catch (loi) {
        // SEATS_UNAVAILABLE khong phai loi hiem: trong mot buoi ban ve dong, co nguoi nhanh
        // tay hon la chuyen thuong. Hien dung ghe bi mat roi tai lai so do.
        oLoi.innerHTML = `<p class="loi">${loi.message}</p>`;
        dangChon.clear();
        await taiLaiSoDo();
    }
}

function hienDaGiu(ketQua) {
    document.getElementById('thanh-hanh-dong').innerHTML = `
        <span>Da giu ${ketQua.seats.join(', ')} — con
            <strong class="dem-nguoc" id="dem-nguoc">--:--</strong></span>
        <a href="/checkout.html?bookingId=${ketQua.bookingId}"><button>Thanh toan</button></a>`;

    demNguoc(new Date(ketQua.holdExpiresAt).getTime());
}

function demNguoc(hetHanLuc) {
    const o = document.getElementById('dem-nguoc');

    const nhip = setInterval(() => {
        const conLai = Math.max(0, Math.round((hetHanLuc - Date.now()) / 1000));
        const phut = String(Math.floor(conLai / 60)).padStart(2, '0');
        const giay = String(conLai % 60).padStart(2, '0');
        o.textContent = `${phut}:${giay}`;
        o.classList.toggle('gap', conLai <= 60);

        if (conLai === 0) {
            clearInterval(nhip);
            // Het gio: ghe co the ve tay nguoi khac bat cu luc nao. Quay lai so do thay vi
            // de nguoi dung bam Thanh toan roi an mot loi 410.
            location.reload();
        }
    }, 1000);
}

function ngheRealtime() {
    ketNoi(`/topic/showtimes/${showtimeId}`, (tin) => {
        // To ngay nhung ghe duoc nhac toi cho phan hoi tuc thi, ROI doc lai tu server.
        // Ban tin la tin hieu "co gi do vua doi", khong phai nguon su that: client vao giua
        // chung hoac mat ket noi vai giay se co buc tranh sai neu chi nghe no.
        taiLaiSoDo(tin.seats ?? []);
    });
}

async function taiLaiSoDo(nhanVuaDoi = []) {
    danhSachGhe = await get(`/showtimes/${showtimeId}/seats`);

    for (const ghe of danhSachGhe) {
        // Ghe minh dang chon ma vua bi nguoi khac lay thi bo chon. Ghe MINH giu thi giu nguyen.
        if (ghe.status !== 'AVAILABLE' && !daGiu.has(ghe.seatId)) {
            dangChon.delete(ghe.seatId);
        }
        // Ghe minh giu ma da duoc nha (sweeper hoac thanh toan that bai) thi khong con la cua minh.
        if (ghe.status === 'AVAILABLE' && daGiu.has(ghe.seatId)) {
            daGiu.delete(ghe.seatId);
        }
    }

    veSoDo(nhanVuaDoi);
    capNhatThanhHanhDong();
}
