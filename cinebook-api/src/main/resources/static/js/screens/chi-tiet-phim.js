import { get } from "../api.js";
import { thoatHtml } from "../an-toan.js";
import { khoiChu } from "../ui/skeleton.js";
import { dinhDangTien } from "../ghe.js";

export function render({ id }) {
    const el = document.createElement("div");
    el.className = "max-w-5xl mx-auto px-4 py-8";
    el.append(khoiChu(4));

    Promise.all([get(`/movies/${id}`), get(`/showtimes?movieId=${id}&from=${new Date().toISOString()}`)])
        .then(([phim, suat]) => el.replaceChildren(noiDung(phim, suat)))
        .catch(() => { el.innerHTML =
            `<div class="serif text-2xl text-center py-20">Khong tim thay phim nay</div>`; });

    return el;
}

function noiDung(phim, suat) {
    const el = document.createElement("div");
    el.innerHTML = `
      <a href="#/" class="text-chuMo text-sm no-underline">&larr; Tat ca phim</a>
      <div class="grid md:grid-cols-[220px_1fr] gap-7 mt-4">
        <div class="aspect-[2/3] rounded-md bg-gradient-to-br from-[#2E2E38] to-[#141418]"></div>
        <div>
          <h1 class="serif text-4xl leading-tight m-0">${thoatHtml(phim.title)}</h1>
          <div class="text-chuMo text-sm mt-2 tracking-wide">
            ${phim.durationMin} PHUT · ${phim.ageRating}</div>
          <p class="mt-4 leading-relaxed max-w-prose">${thoatHtml(phim.description ?? "")}</p>
          <h2 class="serif text-xl mt-8 mb-3">Suat chieu</h2>
          ${suat.length ? danhSachSuat(suat)
            : `<p class="text-chuMo">Chua co suat chieu nao sap toi.</p>`}
        </div>
      </div>`;
    return el;
}

function danhSachSuat(suat) {
    return `<div class="flex flex-col gap-2">` + suat.map((s) => {
        const gio = new Date(s.startAt).toLocaleTimeString("vi-VN",
            { hour: "2-digit", minute: "2-digit" });
        return `<a href="#/suat/${s.showtimeId}/ghe"
                   class="flex items-center gap-4 bg-the border border-vien rounded-lg
                          px-3 py-2.5 no-underline text-chu hover:border-nhan transition">
          <span class="so text-nhan text-lg w-14">${gio}</span>
          <span class="flex-1 text-sm">${thoatHtml(s.cinemaName)}
            <span class="text-chuMo">· ${thoatHtml(s.roomName)}</span></span>
          <span class="so text-sm">${dinhDangTien(s.basePrice)}d</span></a>`;
    }).join("") + `</div>`;
}
