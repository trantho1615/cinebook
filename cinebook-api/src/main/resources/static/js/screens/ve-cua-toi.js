import { get } from "../api.js";
import { thoatHtml } from "../an-toan.js";
import { khoiChu } from "../ui/skeleton.js";

export function render() {
    const el = document.createElement("div");
    el.className = "max-w-2xl mx-auto px-4 py-8";
    el.innerHTML = `<h1 class="serif text-3xl mb-5">Ve cua toi</h1>`;
    const boc = document.createElement("div");
    boc.append(khoiChu(3));
    el.append(boc);

    get("/bookings")
        .then((ds) => boc.replaceChildren(ds.length ? danhSach(ds) : rong()))
        .catch(() => { boc.innerHTML = `<p class="text-chuMo">Khong tai duoc danh sach ve.</p>`; });

    return el;
}

const MAU_TRANG_THAI = {
    CONFIRMED: ["Da xac nhan", "border-[#2F5C3A] text-thanhCong"],
    PENDING:   ["Cho thanh toan", "border-[#5C4A22] text-nhan"],
    EXPIRED:   ["Het han", "border-vien text-chuMo"],
    CANCELLED: ["Da huy", "border-vien text-chuMo"],
    REFUNDED:  ["Da hoan tien", "border-vien text-chuMo"],
};

function danhSach(ds) {
    const el = document.createElement("div");
    el.className = "flex flex-col gap-2";
    el.innerHTML = ds.map((v) => {
        const [ten, lop] = MAU_TRANG_THAI[v.status] ?? [v.status, "border-vien text-chuMo"];
        const cu = v.status !== "CONFIRMED" && v.status !== "PENDING";
        return `<a href="#/ve/${v.bookingId}"
                   class="flex gap-3 bg-the border border-vien rounded-lg p-3 no-underline text-chu
                          ${cu ? "opacity-60" : ""} hover:border-nhan transition">
          <div class="w-10 aspect-[2/3] rounded bg-gradient-to-br from-[#2E2E38] to-[#141418]"></div>
          <div class="flex-1">
            <div class="text-sm">${thoatHtml(v.movieTitle)}</div>
            <div class="text-chuMo text-xs mt-0.5">${new Date(v.startAt)
              .toLocaleString("vi-VN", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" })}</div>
            <div class="so text-nhan text-xs mt-1">${thoatHtml(v.code)}</div></div>
          <span class="text-[10px] uppercase tracking-wide border rounded px-2 py-1 h-fit ${lop}">${ten}</span>
        </a>`;
    }).join("");
    return el;
}

function rong() {
    const el = document.createElement("div");
    el.className = "text-center py-20";
    el.innerHTML = `<div class="serif text-2xl">Chua co ve nao</div>
      <p class="text-chuMo mt-2">Ve ban dat se hien o day.</p>
      <a href="#/" class="inline-block mt-5 bg-nhan text-nen font-bold px-4 py-2 rounded-md no-underline">
        Xem phim dang chieu</a>`;
    return el;
}
