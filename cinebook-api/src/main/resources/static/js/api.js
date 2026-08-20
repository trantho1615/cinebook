// Lop bong duy nhat giua UI va backend. Gan token, tu lam moi phien khi het han, va dich
// loi cua backend thanh Error co nghia.

const KHOA_TOKEN = 'cinebook.token';
const KHOA_REFRESH = 'cinebook.refresh';

export function token() {
    return sessionStorage.getItem(KHOA_TOKEN);
}

export function dangXuat() {
    sessionStorage.removeItem(KHOA_TOKEN);
    sessionStorage.removeItem(KHOA_REFRESH);
}

export async function dangNhap(email, matKhau) {
    return luuPhien(await post('/auth/login', { email, password: matKhau }));
}

/**
 * Tao tai khoan demo neu chua co roi dang nhap.
 *
 * DemoDataSeeder chi nap phim/rap/lich chieu, khong nap nguoi dung — bang users thuoc
 * quyen so huu cua module identity, va viet thang vao do tu catalog la pha dung nguyen tac
 * ma ca du an dua vao. Nen tai khoan demo duoc tao qua chinh API dang ky cong khai.
 */
export async function dangNhapDemo() {
    const email = 'demo@cinebook.local';
    const matKhau = 'MatKhauRatManh123';

    try {
        await post('/auth/register', {
            email, password: matKhau, fullName: 'Khach Demo', phone: '0900000000',
        });
    } catch (loi) {
        // Da dang ky tu lan demo truoc thi di tiep. Moi loi khac thi de no noi len.
        if (loi.status !== 409) {
            throw loi;
        }
    }
    return dangNhap(email, matKhau);
}

export function get(duongDan) {
    return goi('GET', duongDan);
}

export function post(duongDan, than) {
    return goi('POST', duongDan, than ?? {});
}

function luuPhien(ketQua) {
    // sessionStorage chu khong phai localStorage: dong tab la mat phien. Ca hai deu doc
    // duoc bang JavaScript nen deu khong chong duoc XSS — sessionStorage chi thu hep cua so
    // thoi gian. Cach dung cho san pham that la refresh token trong cookie HttpOnly, va do
    // la viec cua mot milestone khac.
    sessionStorage.setItem(KHOA_TOKEN, ketQua.accessToken);
    sessionStorage.setItem(KHOA_REFRESH, ketQua.refreshToken);
    return ketQua;
}

/**
 * Mot lan lam moi duy nhat tai mot thoi diem.
 *
 * Refresh token cua Milestone 2 xoay vong va co phat hien tai su dung: dung mot token hai
 * lan bi coi la dau hieu token bi danh cap va CA HO token bi thu hoi. Trang so do ghe ban
 * ra nhieu request song song, nen hai request cung dinh 401 se cung goi /auth/refresh voi
 * cung mot token — va tu khoa chinh minh ra ngoai. Gom chung vao mot promise duy nhat.
 */
let dangLamMoi = null;

function lamMoiPhien() {
    if (!dangLamMoi) {
        const refreshToken = sessionStorage.getItem(KHOA_REFRESH);
        dangLamMoi = (refreshToken
            ? goi('POST', '/auth/refresh', { refreshToken }, false).then(luuPhien)
            : Promise.reject(new Error('Khong co refresh token')))
            .finally(() => {
                dangLamMoi = null;
            });
    }
    return dangLamMoi;
}

async function goi(phuongThuc, duongDan, than, choPhepLamMoi = true) {
    const res = await fetch(duongDan, {
        method: phuongThuc,
        headers: {
            'Content-Type': 'application/json',
            ...(token() ? { Authorization: `Bearer ${token()}` } : {}),
        },
        body: than === undefined ? undefined : JSON.stringify(than),
    });

    if (res.status === 401 && token() && choPhepLamMoi) {
        // Access token song 15 phut, ma mot trang so do ghe co the mo lau hon the. Thu lam
        // moi phien roi goi lai dung mot lan; that bai thi moi dua ve trang dang nhap.
        try {
            await lamMoiPhien();
            return goi(phuongThuc, duongDan, than, false);
        } catch {
            dangXuat();
            location.href = '/index.html?phien=het-han';
            throw new Error('Phien dang nhap da het han');
        }
    }

    if (!res.ok) {
        // Backend tra ApiError {code, message} cho moi loi nghiep vu. Hien message cho nguoi
        // dung, giu code de UI phan biet khi can (SEATS_UNAVAILABLE, HOLD_EXPIRED).
        const loi = await res.json().catch(() => ({ message: res.statusText }));
        throw Object.assign(new Error(loi.message || res.statusText), {
            code: loi.code,
            status: res.status,
        });
    }

    return res.status === 204 ? null : res.json();
}
