// Thoat HTML cho moi gia tri tu server truoc khi noi suy vao innerHTML.
//
// Ten phim, ten rap, email, ma ve deu la du lieu nguoi khac nhap duoc. Phan biet
// "cho nay an toan" bang mat la cach bo sot: thoat het, khong ngoai le.

export function thoatHtml(giaTri) {
    return String(giaTri ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}
