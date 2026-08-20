// Lop bong duy nhat giua UI va backend. Gan token, dich loi cua backend thanh Error co
// nghia, va xu ly phien het han o mot cho.

const KHOA_TOKEN = 'cinebook.token';

export function token() {
    return sessionStorage.getItem(KHOA_TOKEN);
}

export function dangXuat() {
    sessionStorage.removeItem(KHOA_TOKEN);
}

export async function dangNhap(email, matKhau) {
    const ketQua = await post('/auth/login', { email, password: matKhau });
    // sessionStorage chu khong phai localStorage: dong tab la mat token. Ca hai deu doc
    // duoc bang JavaScript nen deu khong chong duoc XSS — sessionStorage chi thu hep cua
    // so thoi gian. Cach dung cho san pham that la refresh token trong cookie HttpOnly,
    // va do la viec cua mot milestone khac.
    sessionStorage.setItem(KHOA_TOKEN, ketQua.accessToken);
    return ketQua;
}

/**
 * Tao tai khoan demo neu chua co roi dang nhap.
 *
 * DemoDataSeeder chi nap phim/rap/lich chieu, khong nap nguoi dung — bang users thuoc
 * quyen so huu cua module identity, va viet thang vao do tu catalog la pha dung nguyen
 * tac ma ca du an dua vao. Nen tai khoan demo duoc tao qua chinh API dang ky cong khai.
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

async function goi(phuongThuc, duongDan, than) {
    const res = await fetch(duongDan, {
        method: phuongThuc,
        headers: {
            'Content-Type': 'application/json',
            ...(token() ? { Authorization: `Bearer ${token()}` } : {}),
        },
        body: than === undefined ? undefined : JSON.stringify(than),
    });

    if (res.status === 401 && token()) {
        // Access token song 15 phut. Het han giua chung la chuyen binh thuong chu khong
        // phai loi — dua nguoi dung ve trang dang nhap thay vi hien mot thong bao kho hieu.
        dangXuat();
        location.href = '/index.html';
        throw new Error('Phien dang nhap da het han');
    }

    if (!res.ok) {
        // Backend tra ApiError {code, message} cho moi loi nghiep vu. Hien message cho
        // nguoi dung, giu code de UI phan biet khi can (SEATS_UNAVAILABLE, HOLD_EXPIRED).
        const loi = await res.json().catch(() => ({ message: res.statusText }));
        throw Object.assign(new Error(loi.message || res.statusText), {
            code: loi.code,
            status: res.status,
        });
    }

    return res.status === 204 ? null : res.json();
}
