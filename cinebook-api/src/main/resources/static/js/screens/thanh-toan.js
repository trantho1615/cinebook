import { get, post } from "../api.js";
import { thoatHtml } from "../an-toan.js";
import { dinhDangTien } from "../ghe.js";
import { moHopThoai } from "../ui/hopthoai.js";

let dongHo = null;
let doiKetQua = null;
let soLuot = 0;

export function render({ id }) {
    const luot = ++soLuot;
    const el = document.createElement("div");
    el.className = "max-w-md mx-auto px-4 py-10";
    el.innerHTML = `<div class="text-chuMo">Dang tai don...</div>`;

    get(`/bookings/${id}`)
        .then((don) => { if (luot === soLuot) dung(don); })
        .catch(() => {
            if (luot !== soLuot) return;
            el.innerHTML = `<div class="serif text-2xl text-center py-20">Khong tim thay don</div>`;
        });

    function dung(don) {
        el.innerHTML = `
          <h1 class="serif text-3xl mb-5">Thanh toan</h1>
          <div class="bg-the border border-vien rounded-lg p-4">
            <div class="flex justify-between text-sm"><span class="text-chuMo">Ma don</span>
              <span class="so">${thoatHtml(don.code)}</span></div>
            <div class="flex justify-between text-sm mt-2"><span class="text-chuMo">Ghe</span>
              <span>${thoatHtml((don.seats ?? []).join(", "))}</span></div>
            <div class="flex justify-between items-baseline mt-3 pt-3 border-t border-vien">
              <span class="text-chuMo">Tong</span>
              <span class="so text-nhan text-xl font-semibold">${dinhDangTien(don.totalAmount)}d</span></div>
          </div>
          <div class="bg-theNoi rounded-lg p-3 my-4 text-center">
            <div class="text-chuMo text-[10px] tracking-wider uppercase">Giu cho con</div>
            <div id="conlai" class="so text-2xl mt-1">--:--</div>
          </div>
          <p id="loi" class="text-loi text-sm hidden mb-3"></p>
          <button id="tra" class="w-full bg-nhan text-nen font-bold py-3 rounded-md border-0 cursor-pointer">
            Thanh toan</button>
          <div id="cong" class="hidden mt-4 bg-the border border-vien rounded-lg p-4"></div>`;

        demNguoc(new Date(don.holdExpiresAt));
        el.querySelector("#tra").onclick = () => khoiTaoGiaoDich(don);
    }

    function baoLoi(tin) {
        const o = el.querySelector("#loi");
        o.textContent = tin;
        o.classList.remove("hidden");
    }

    async function khoiTaoGiaoDich(don) {
        const nut = el.querySelector("#tra");
        nut.disabled = true;
        try {
            // Tra ve PaymentResponse {paymentId, redirectUrl, amount}. Endpoint KHONG nhan body.
            const giaoDich = await post(`/bookings/${don.bookingId}/payments`, {});
            veCongGiaLap(don, giaoDich.paymentId);
        } catch (e) {
            nut.disabled = false;
            baoLoi(e.message ?? "Khong khoi tao duoc giao dich.");
        }
    }

    /**
     * Cong thanh toan gia lap.
     *
     * Luong nay CAN HAI buoc, khong phai mot: POST /bookings/{id}/payments chi tao giao dich
     * o trang thai cho, con webhook chi den khi ai do goi POST /demo/payments/{id}/{ketQua}.
     * Chi lam buoc dau roi ngoi doi la doi mai mai.
     */
    function veCongGiaLap(don, paymentId) {
        const cong = el.querySelector("#cong");
        cong.classList.remove("hidden");
        cong.innerHTML = `
          <div class="text-chuMo text-[10px] tracking-wider uppercase">Cong thanh toan gia lap</div>
          <p class="text-chuMo text-sm mt-2 leading-relaxed">
            Khong co tien that o day. Chon ket qua de gia lap phan hoi cua cong thanh toan.</p>
          <div class="flex gap-2 mt-3">
            <button id="ok" class="flex-1 bg-nhan text-nen font-bold py-2.5 rounded-md border-0 cursor-pointer">
              Gia lap thanh cong</button>
            <button id="hong" class="flex-1 border border-vien text-chu py-2.5 rounded-md bg-transparent cursor-pointer">
              Gia lap that bai</button>
          </div>
          <div id="cho" class="hidden mt-3 text-chuMo text-sm">
            Dang cho cong thanh toan. Ghe cua ban van duoc giu, trang se tu cap nhat.</div>`;
        cong.querySelector("#ok").onclick = () => giaiLap(don, paymentId, "succeed");
        cong.querySelector("#hong").onclick = () => giaiLap(don, paymentId, "fail");
    }

    async function giaiLap(don, paymentId, ketQua) {
        const cong = el.querySelector("#cong");
        cong.querySelector("#ok").disabled = true;
        cong.querySelector("#hong").disabled = true;
        cong.querySelector("#cho").classList.remove("hidden");
        try {
            await post(`/demo/payments/${paymentId}/${ketQua}`, {});
        } catch (e) {
            // Cong gia lap chi ton tai o profile demo.
            baoLoi(e.status === 404
                ? "Cong gia lap chi co o profile demo."
                : (e.message ?? "Khong gia lap duoc ket qua."));
            return;
        }
        if (ketQua === "fail") {
            baoLoi("Thanh toan that bai. Ghe van duoc giu cho toi khi het gio.");
            cong.querySelector("#cho").classList.add("hidden");
            cong.querySelector("#ok").disabled = false;
            cong.querySelector("#hong").disabled = false;
            return;
        }
        doiXacNhan(don);
    }

    /**
     * Webhook la bat dong bo — no co the toi muon. Hoi lai thay vi bat nguoi dung bam F5.
     */
    function doiXacNhan(don) {
        doiKetQua = setInterval(async () => {
            if (luot !== soLuot) { clearInterval(doiKetQua); doiKetQua = null; return; }
            const moi = await get(`/bookings/${don.bookingId}`);
            if (moi.status === "CONFIRMED") {
                clearInterval(doiKetQua); doiKetQua = null;
                location.hash = `#/ve/${don.bookingId}`;
            }
        }, 1000);
    }

    function demNguoc(hetHan) {
        const o = el.querySelector("#conlai");
        // KHONG dat aria-live o day: no se doc moi giay mot lan va bien trinh doc man hinh
        // thanh cuc hinh.
        dongHo = setInterval(() => {
            const giay = Math.floor((hetHan - Date.now()) / 1000);
            if (giay <= 0) {
                clearInterval(dongHo); dongHo = null;
                moHopThoai({
                    tieuDe: "Het thoi gian giu cho",
                    than: "Ghe da duoc tra lai cho nguoi khac. Ban co the chon lai.",
                    nhan: "Chon lai ghe",
                    khiBam: () => { location.hash = "#/"; },
                });
                return;
            }
            o.textContent = `${String(Math.floor(giay / 60)).padStart(2, "0")}:${String(giay % 60).padStart(2, "0")}`;
        }, 250);
    }

    return el;
}

export function huyBo() {
    soLuot++;
    if (dongHo) { clearInterval(dongHo); dongHo = null; }
    if (doiKetQua) { clearInterval(doiKetQua); doiKetQua = null; }
}
