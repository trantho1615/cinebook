import { dangNhap, dangNhapDemo, dangXuat, get, token } from './api.js';

const app = document.getElementById('app');
const oNguoiDung = document.getElementById('nguoi-dung');

if (token()) {
    hienLichChieu();
} else {
    hienFormDangNhap();
}

function hienFormDangNhap() {
    oNguoiDung.textContent = '';
    app.innerHTML = `
        <form class="the" id="form-dang-nhap">
            <h2>Dang nhap</h2>
            <div id="loi"></div>
            <label for="email">Email</label>
            <input id="email" type="email" autocomplete="username" required>
            <label for="mat-khau">Mat khau</label>
            <input id="mat-khau" type="password" autocomplete="current-password" required>
            <p>
                <button type="submit">Dang nhap</button>
                <button type="button" class="phu" id="nut-demo">Dung tai khoan demo</button>
            </p>
        </form>`;

    document.getElementById('form-dang-nhap').addEventListener('submit', (e) => {
        e.preventDefault();
        thu(() => dangNhap(
            document.getElementById('email').value,
            document.getElementById('mat-khau').value));
    });

    document.getElementById('nut-demo').addEventListener('click', () => thu(dangNhapDemo));
}

async function thu(hanhDong) {
    const oLoi = document.getElementById('loi');
    oLoi.innerHTML = '';
    try {
        await hanhDong();
        await hienLichChieu();
    } catch (loi) {
        oLoi.innerHTML = `<p class="loi">${loi.message}</p>`;
    }
}

async function hienLichChieu() {
    app.innerHTML = '<p class="dang-tai">Dang tai lich chieu...</p>';
    oNguoiDung.innerHTML = 'Da dang nhap · <a href="#" id="nut-thoat">thoat</a>';
    document.getElementById('nut-thoat').addEventListener('click', (e) => {
        e.preventDefault();
        dangXuat();
        hienFormDangNhap();
    });

    // from=bay gio: /showtimes la mot truy van tim kiem tong quat, mac dinh tra ve ca suat
    // da chieu (dung cho bao cao, va cho AI agent o phase 2 hoi mot cua so thoi gian bat
    // ky). Khach duyet lich thi chi quan tam nhung suat con dat duoc.
    const tuBayGio = encodeURIComponent(new Date().toISOString());
    const danhSach = await get(`/showtimes?from=${tuBayGio}`);

    if (danhSach.length === 0) {
        app.innerHTML = '<p class="trong">Khong con suat chieu nao sap toi.</p>';
        return;
    }

    const theoPhim = new Map();
    for (const suat of danhSach) {
        if (!theoPhim.has(suat.movieTitle)) {
            theoPhim.set(suat.movieTitle, []);
        }
        theoPhim.get(suat.movieTitle).push(suat);
    }

    app.innerHTML = [...theoPhim.entries()]
        .map(([tenPhim, suatChieu]) => `
            <section class="the">
                <h2>${tenPhim}</h2>
                ${suatChieu.slice(0, 8).map(khungGio).join('')}
            </section>`)
        .join('');
}

function khungGio(suat) {
    // Backend tra thoi diem theo UTC ("2026-08-21T09:00:00Z"); nguoi xem can gio dia phuong.
    const gio = new Date(suat.startAt).toLocaleString('vi-VN', {
        weekday: 'short', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
    });
    return `<p>
        <a href="/seats.html?showtimeId=${suat.showtimeId}">${gio}</a>
        — ${suat.cinemaName} (${suat.district}), ${suat.roomName}
        — ${suat.basePrice.toLocaleString('vi-VN')} d
    </p>`;
}
