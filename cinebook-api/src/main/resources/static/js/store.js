// Trang thai dung chung giua cac man hinh. Khong dung thu vien: hai gia tri va mot danh
// sach nguoi nghe la du, va them mot thu vien quan ly trang thai o day la thua.

let _nguoiDung = null;
let _gheDangChon = [];
const nguoiNghe = new Set();

function bao() { for (const fn of nguoiNghe) fn(); }

export function nguoiDung() { return _nguoiDung; }
export function datNguoiDung(u) { _nguoiDung = u; bao(); }
export function gheDangChon() { return _gheDangChon; }
export function datGheDangChon(ds) { _gheDangChon = ds; bao(); }

/** Tra ve ham go dang ky — man hinh PHAI goi no trong huyBo(). */
export function theoDoi(fn) {
    nguoiNghe.add(fn);
    return () => nguoiNghe.delete(fn);
}
