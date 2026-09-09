import { get } from "../api.js";
import { thoatHtml } from "../an-toan.js";
import { luoiPhim } from "../ui/skeleton.js";

export function render() {
    const el = document.createElement("div");
    el.className = "max-w-5xl mx-auto px-4 py-8";
    el.innerHTML = `<h1 class="serif text-3xl mb-6">Dang chieu</h1>`;
    const boc = document.createElement("div");
    boc.append(luoiPhim());
    el.append(boc);

    get("/movies?status=NOW_SHOWING")
        .then((ds) => boc.replaceChildren(ds.length ? luoi(ds) : rong()))
        .catch(() => boc.replaceChildren(loi()));

    return el;
}

function luoi(ds) {
    const el = document.createElement("div");
    el.className = "grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5";
    el.innerHTML = ds.map((p) => `
      <a href="#/phim/${thoatHtml(p.id)}" class="no-underline text-chu group">
        <div class="aspect-[2/3] rounded-md bg-gradient-to-br from-[#2E2E38] to-[#141418]
                    group-hover:ring-1 group-hover:ring-nhan transition"></div>
        <div class="mt-2.5 text-sm leading-snug">${thoatHtml(p.title)}</div>
        <div class="text-chuMo text-xs mt-0.5">${thoatHtml(p.durationMin)} phut · ${thoatHtml(p.ageRating)}</div>
      </a>`).join("");
    return el;
}

function rong() {
    const el = document.createElement("div");
    el.className = "text-center py-20";
    el.innerHTML = `<div class="serif text-2xl">Chua co phim nao dang chieu</div>
      <p class="text-chuMo mt-2">Quay lai sau nhe.</p>`;
    return el;
}

function loi() {
    const el = document.createElement("div");
    el.className = "text-center py-20";
    el.innerHTML = `<div class="serif text-2xl">Khong tai duoc danh sach phim</div>
      <p class="text-chuMo mt-2">Kiem tra ket noi roi thu lai.</p>
      <button class="mt-5 border border-vien text-chu px-4 py-2 rounded-md bg-transparent cursor-pointer">
        Thu lai</button>`;
    el.querySelector("button").onclick = () => location.reload();
    return el;
}
