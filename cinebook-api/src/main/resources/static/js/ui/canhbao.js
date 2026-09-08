// Banner trong trang. Dung cho tinh huong con cuu duoc: mat ket noi realtime.
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
