// Banner trong trang. Dung cho tinh huong con cuu duoc: mat ket noi realtime.
//
// HOP DONG: tieuDe, than va nhanNut duoc noi thang vao innerHTML, KHONG tu thoat o day.
// Moi noi goi da tu thoatHtml() gia tri server truoc khi truyen vao. Thoat lai o day se
// bien "&amp;" that su thanh "&amp;amp;" hien thi cho nguoi dung. Noi goi moi phai tu thoat.
export function dungCanhBao({ tieuDe, than, nhanNut, khiBam }) {
    const el = document.createElement("div");
    el.className = "border border-[#5C4A22] bg-[#1A160E] rounded-lg p-3 mb-4";
    el.setAttribute("role", "status");
    el.innerHTML = `
      <div class="text-sm">${tieuDe}</div>
      <div class="text-chuMo text-sm mt-1">${than}</div>
      ${nhanNut ? `<button class="mt-2.5 border border-vien text-chu px-3 py-1.5 rounded-md
                                  bg-transparent cursor-pointer text-sm">${nhanNut}</button>` : ""}`;
    const nut = el.querySelector("button");
    if (nut) nut.onclick = khiBam;
    return el;
}
