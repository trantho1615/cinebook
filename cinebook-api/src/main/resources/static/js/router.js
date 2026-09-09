// Hash router viet tay. Vi sao khong dung History API: no can Spring forward moi duong
// dan la ve index.html, va cau hinh do de nuot nham /movies, /bookings cua chinh API.
// Hash khong bao gio di toi server, nen khong co nguy co do.

const TUYEN = [
    [/^#?\/?$/,                    () => ({ ten: "phim",        thamSo: {} })],
    [/^#\/phim\/([^/]+)$/,         (m) => ({ ten: "chiTietPhim", thamSo: { id: m[1] } })],
    [/^#\/suat\/([^/]+)\/ghe$/,    (m) => ({ ten: "soDoGhe",     thamSo: { id: m[1] } })],
    [/^#\/thanh-toan\/([^/]+)$/,   (m) => ({ ten: "thanhToan",   thamSo: { id: m[1] } })],
    [/^#\/ve-cua-toi$/,            () => ({ ten: "veCuaToi",     thamSo: {} })],
    [/^#\/ve\/([^/]+)$/,           (m) => ({ ten: "chiTietVe",   thamSo: { id: m[1] } })],
    [/^#\/dang-nhap$/,             () => ({ ten: "dangNhap",     thamSo: {} })],
];

export function phanGiai(hash) {
    for (const [mau, dung] of TUYEN) {
        const khop = mau.exec(hash);
        if (khop) return dung(khop);
    }
    return { ten: "khongTimThay", thamSo: {} };
}

// Tuyen doi dang nhap. Khach vang lai duoc xem phim va so do ghe, nhung khong the giu ghe,
// thanh toan hay xem ve cua minh — va roi vao mot man hinh loi trong khong co loi ra la
// mot ngo cut, khong phai mot trang thai.
export const CAN_DANG_NHAP = new Set(["veCuaToi", "chiTietVe", "thanhToan"]);

/**
 * dinhTuyen: { ten -> { render(thamSo) -> HTMLElement, huyBo?() } }
 * boc: phan tu DOM de gan man hinh vao.
 * daDangNhap: () -> boolean. Truyen vao thay vi import truc tiep store/api de router
 * khong phu thuoc vao trang thai toan cuc va van test duoc nhu mot ham thuan.
 *
 * huyBo() cua man hinh CU luon duoc goi truoc khi dung man hinh moi. Thieu buoc nay thi
 * WebSocket cua so do ghe khong bao gio dong va dong ho dem nguoc chay ngam mai mai.
 */
export function khoiTao(dinhTuyen, boc, daDangNhap) {
    let dangHien = null;

    function ve() {
        if (dangHien && typeof dangHien.huyBo === "function") dangHien.huyBo();
        const { ten, thamSo } = phanGiai(location.hash);
        if (CAN_DANG_NHAP.has(ten) && !daDangNhap()) {
            location.hash = "#/dang-nhap";
            return;
        }
        dangHien = dinhTuyen[ten] || dinhTuyen.khongTimThay;
        boc.replaceChildren(dangHien.render(thamSo));
        window.scrollTo(0, 0);
    }

    window.addEventListener("hashchange", ve);
    ve();
}
