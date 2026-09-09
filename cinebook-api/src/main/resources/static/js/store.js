// Trang thai dung chung giua cac man hinh. Khong dung thu vien: hai gia tri va mot danh
// sach nguoi nghe la du, va them mot thu vien quan ly trang thai o day la thua.

let _nguoiDung = null;
const nguoiNghe = new Set();

function bao() { for (const fn of nguoiNghe) fn(); }

export function nguoiDung() { return _nguoiDung; }
export function datNguoiDung(u) { _nguoiDung = u; bao(); }

/** Tra ve ham go dang ky — man hinh PHAI goi no trong huyBo(). */
export function theoDoi(fn) {
    nguoiNghe.add(fn);
    return () => nguoiNghe.delete(fn);
}

/**
 * Thong bao mot lan cho man hinh ke tiep.
 *
 * api.js dat khi phien het han; man hinh dang-nhap doc VA xoa. Doc mot lan roi mat la co y:
 * bam F5 o trang dang nhap khong nen hien lai mot canh bao da cu.
 */
let _tinMotLan = null;
export function datTinMotLan(tin) { _tinMotLan = tin; }
export function layTinMotLan() { const t = _tinMotLan; _tinMotLan = null; return t; }
