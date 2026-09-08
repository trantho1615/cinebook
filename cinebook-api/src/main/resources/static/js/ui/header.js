import { nguoiDung, datNguoiDung, theoDoi } from "../store.js";
import { dangXuat } from "../api.js";

export function dungHeader() {
    const el = document.createElement("header");
    el.className = "border-b border-vien";

    function ve() {
        const u = nguoiDung();
        el.innerHTML = `
          <div class="max-w-5xl mx-auto px-4 py-3 flex items-center gap-5">
            <a href="#/" class="serif text-xl text-chu no-underline">cinebook</a>
            <nav class="flex gap-4 text-sm text-chuMo">
              <a href="#/" class="text-chuMo no-underline hover:text-chu">Phim</a>
              ${u ? '<a href="#/ve-cua-toi" class="text-chuMo no-underline hover:text-chu">Ve cua toi</a>' : ""}
            </nav>
            <div class="ml-auto text-sm">
              ${u
                ? `<span class="text-chuMo mr-3">${u.email}</span>
                   <button id="thoat" class="text-chuMo hover:text-chu bg-transparent border-0 cursor-pointer">Thoat</button>`
                : `<a href="#/dang-nhap" class="text-nhan no-underline">Dang nhap</a>`}
            </div>
          </div>`;
        const nut = el.querySelector("#thoat");
        if (nut) nut.onclick = () => { dangXuat(); datNguoiDung(null); location.hash = "#/"; };
    }

    theoDoi(ve);   // Header song ca vong doi ung dung nen khong can go dang ky.
    ve();
    return el;
}
