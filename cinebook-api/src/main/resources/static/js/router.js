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

/**
 * dinhTuyen: { ten -> { render(thamSo) -> HTMLElement, huyBo?() } }
 * boc: phan tu DOM de gan man hinh vao.
 *
 * huyBo() cua man hinh CU luon duoc goi truoc khi dung man hinh moi. Thieu buoc nay thi
 * WebSocket cua so do ghe khong bao gio dong va dong ho dem nguoc chay ngam mai mai.
 */
export function khoiTao(dinhTuyen, boc) {
    let dangHien = null;

    function ve() {
        if (dangHien && typeof dangHien.huyBo === "function") dangHien.huyBo();
        const { ten, thamSo } = phanGiai(location.hash);
        dangHien = dinhTuyen[ten] || dinhTuyen.khongTimThay;
        boc.replaceChildren(dangHien.render(thamSo));
        window.scrollTo(0, 0);
    }

    window.addEventListener("hashchange", ve);
    ve();
}
