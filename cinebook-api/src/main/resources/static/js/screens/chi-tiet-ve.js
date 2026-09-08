import { get, del } from "../api.js";
import { thoatHtml } from "../an-toan.js";
import { dinhDangTien } from "../ghe.js";

export function render({ id }) {
    const el = document.createElement("div");
    el.className = "max-w-md mx-auto px-4 py-10";
    el.innerHTML = `<div class="text-chuMo">Dang tai ve...</div>`;

    get(`/bookings/${id}`).then((v) => {
        el.innerHTML = `
          <a href="#/ve-cua-toi" class="text-chuMo text-sm no-underline">&larr; Ve cua toi</a>
          <h1 class="serif text-3xl mt-2">${thoatHtml(v.movieTitle)}</h1>
          <div class="text-chuMo text-sm mb-5">${new Date(v.startAt)
            .toLocaleString("vi-VN", { weekday: "long", day: "2-digit", month: "2-digit",
                                       hour: "2-digit", minute: "2-digit" })}</div>
          <div class="bg-the border border-vien rounded-lg p-5 text-center">
            <div class="text-chuMo text-[10px] tracking-wider uppercase">Ma ve</div>
            <div class="so text-nhan text-3xl mt-1.5">${thoatHtml(v.code)}</div>
            <div class="text-chuMo text-sm mt-4">
              ${thoatHtml((v.seats ?? []).join(", "))}</div>
            <div class="so mt-1">${dinhDangTien(v.totalAmount)}d</div>
          </div>
          ${v.status === "PENDING"
            ? `<button id="huy" class="mt-5 w-full border border-vien text-chuMo py-2.5 rounded-md
                                        bg-transparent cursor-pointer">Huy don</button>`
            : v.status === "CONFIRMED"
              // Backend chi huy duoc don PENDING (CancelBookingUseCase: AND status = 'PENDING').
              // Hien nut cho don da xac nhan la hua mot viec he thong khong lam duoc.
              ? `<p class="text-chuMo text-sm mt-5 text-center">
                   Ve da xac nhan khong huy duoc o day. Lien he rap neu can doi.</p>`
              : ""}`;
        const nut = el.querySelector("#huy");
        if (nut) nut.onclick = async () => {
            nut.disabled = true;
            await del(`/bookings/${id}`);
            location.hash = "#/ve-cua-toi";
        };
    }).catch(() => {
        el.innerHTML = `<div class="serif text-2xl text-center py-20">Khong tim thay ve</div>`;
    });

    return el;
}
