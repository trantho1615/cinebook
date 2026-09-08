import { get, post } from "../api.js";
import { thoatHtml } from "../an-toan.js";
import { thuocTinhGhe, dinhDangTien } from "../ghe.js";
import { ketNoi } from "../realtime.js";
import { moHopThoai } from "../ui/hopthoai.js";
import { dungCanhBao } from "../ui/canhbao.js";

let ketNoiHienTai = null;

export function render({ id }) {
    const el = document.createElement("div");
    el.className = "max-w-5xl mx-auto px-4 py-8 pb-32 lg:pb-8";
    el.innerHTML = `<div class="text-chuMo">Dang tai so do ghe...</div>`;

    let danhSachGhe = [];
    const dangChon = new Set();

    Promise.all([get(`/showtimes/${id}`), get(`/showtimes/${id}/seats`)])
        .then(([suat, ghe]) => { danhSachGhe = ghe; dung(suat); })
        .catch(() => { el.innerHTML =
            `<div class="serif text-2xl text-center py-20">Khong tim thay suat chieu</div>`; });

    function dung(suat) {
        el.innerHTML = `
          <a href="#/phim/${suat.movieId}" class="text-chuMo text-sm no-underline">&larr; Quay lai</a>
          <h1 class="serif text-3xl mt-2 mb-1">${thoatHtml(suat.movieTitle)}</h1>
          <div class="text-chuMo text-sm mb-6">${thoatHtml(suat.cinemaName)} · ${thoatHtml(suat.roomName)} ·
            ${new Date(suat.startAt).toLocaleString("vi-VN", { hour: "2-digit", minute: "2-digit",
              day: "2-digit", month: "2-digit" })}</div>
          <div id="canhbao"></div>
          <div class="grid lg:grid-cols-[1fr_230px] gap-7">
            <div>
              <div class="h-[3px] rounded bg-gradient-to-r from-transparent via-[#2E2E36] to-transparent"></div>
              <div class="text-center text-[10px] tracking-[0.22em] text-[#5E5E68] mt-1.5 mb-4">MAN HINH</div>
              <div id="luoi" class="overflow-x-auto"></div>
              <div id="chuthich" class="flex gap-4 justify-center mt-4 text-xs text-chuMo flex-wrap"></div>
            </div>
            <div id="tomtat"></div>
          </div>
          <div id="thongbao" class="sr-only" aria-live="polite"></div>`;

        veLuoi();
        veChuThich();
        veTomTat();

        ketNoiHienTai = ketNoi(id, {
            khiGheDoi: () => get(`/showtimes/${id}/seats`).then((moi) => {
                danhSachGhe = moi;
                veLuoi();
                el.querySelector("#thongbao").textContent = "So do ghe vua duoc cap nhat";
            }),
            khiMatKetNoi: () => el.querySelector("#canhbao").replaceChildren(dungCanhBao({
                tieuDe: "Mat ket noi realtime",
                than: "So do ghe co the khong phai moi nhat. Dang thu ket noi lai...",
                nhanNut: "Tai lai so do",
                khiBam: () => get(`/showtimes/${id}/seats`)
                    .then((moi) => { danhSachGhe = moi; veLuoi(); }),
            })),
            khiNoiLai: () => el.querySelector("#canhbao").replaceChildren(),
        });
    }

    function veLuoi() {
        const luoi = el.querySelector("#luoi");
        // Nho ghe dang duoc focus TRUOC khi dung lai luoi. replaceChildren vut bo phan tu
        // cu, va focus roi ve <body>. Voi nguoi dung ban phim, chon mot ghe roi mat focus
        // nghia la phai Tab lai tu dau de chon ghe thu hai — dieu do bien phim mui ten
        // thanh vo dung dung o luc no can dung nhat.
        const gheDangFocus = document.activeElement?.dataset?.ghe;
        const theoHang = {};
        for (const g of danhSachGhe) (theoHang[g.rowLabel] ??= []).push(g);

        const boc = document.createElement("div");
        boc.setAttribute("role", "grid");
        boc.setAttribute("aria-label", "So do ghe");
        boc.className = "inline-flex flex-col gap-1.5 mx-auto";

        for (const [hang, ds] of Object.entries(theoHang)) {
            const dong = document.createElement("div");
            dong.setAttribute("role", "row");
            dong.className = "flex gap-1.5";
            for (const g of ds) {
                const t = thuocTinhGhe(g, dangChon.has(g.seatId));
                const nut = document.createElement("button");
                nut.className = t.lop;
                // Ghe da ban KHONG hien so: mau chu du toi de dat WCAG AA tren nen ghe da ban
                // se sang toi muc ghe da ban noi len — nguoc voi y muon. Ten ghe van den
                // duoc trinh doc man hinh qua aria-label.
                nut.textContent = g.status === "BOOKED" ? "" : g.seatNumber;
                nut.setAttribute("role", "gridcell");
                nut.setAttribute("aria-label", t.nhan);
                nut.dataset.ghe = g.seatId;
                nut.disabled = !t.chonDuoc;
                nut.onclick = () => doiChon(g.seatId);
                dong.append(nut);
            }
            boc.append(dong);
        }
        boc.onkeydown = diChuyenBangPhim;
        luoi.replaceChildren(boc);
        if (gheDangFocus) {
            // seatId la uuid nen an toan trong bo chon thuoc tinh.
            el.querySelector(`#luoi button[data-ghe="${gheDangFocus}"]`)?.focus();
        }
    }

    /**
     * Phim mui ten di chuyen trong luoi. Mac dinh cua trinh duyet la Tab qua tung nut —
     * voi 96 ghe do la 96 lan nhan Tab de toi cuoi phong.
     */
    function diChuyenBangPhim(e) {
        const huong = { ArrowRight: 1, ArrowLeft: -1 };
        if (!(e.key in huong) && e.key !== "ArrowUp" && e.key !== "ArrowDown") return;
        e.preventDefault();
        const nut = [...el.querySelectorAll("#luoi button")];
        const i = nut.indexOf(document.activeElement);
        if (i < 0) return;
        const soCot = el.querySelectorAll('#luoi [role="row"]')[0].children.length;
        const buoc = e.key in huong ? huong[e.key] : (e.key === "ArrowDown" ? soCot : -soCot);
        const ke = nut[i + buoc];
        if (ke) ke.focus();
    }

    function doiChon(seatId) {
        dangChon.has(seatId) ? dangChon.delete(seatId) : dangChon.add(seatId);
        veLuoi();
        veTomTat();
    }

    function veChuThich() {
        el.querySelector("#chuthich").innerHTML = [
            ["ghe-trong", "Trong"], ["ghe-toi", "Ban chon"],
            ["ghe-khac", "Nguoi khac giu"], ["ghe-ban", "Da ban"],
        ].map(([lop, ten]) =>
            `<span><i class="ghe ${lop} !w-3 !h-3 align-middle inline-block"></i> ${ten}</span>`).join("");
    }

    function veTomTat() {
        const chon = danhSachGhe.filter((g) => dangChon.has(g.seatId));
        const tong = chon.reduce((s, g) => s + g.price, 0);
        const t = el.querySelector("#tomtat");
        t.className = chon.length
            ? "fixed bottom-0 left-0 right-0 lg:static bg-the border-t lg:border border-vien lg:rounded-lg p-4 z-40"
            : "hidden lg:block bg-the border border-vien rounded-lg p-4 text-chuMo text-sm";
        if (!chon.length) { t.textContent = "Chon ghe de tiep tuc."; return; }
        t.innerHTML = `
          <div class="text-chuMo text-[10px] tracking-wider uppercase">Ghe da chon</div>
          <div class="text-base mt-1.5">${chon.map((g) => g.label).join(", ")}</div>
          <div class="flex justify-between items-baseline mt-3 pt-2.5 border-t border-vien">
            <span class="text-chuMo text-sm">Tong</span>
            <span class="so text-nhan text-lg font-semibold">${dinhDangTien(tong)}d</span></div>
          <button id="giu" class="mt-3 w-full bg-nhan text-nen font-bold py-2.5 rounded-md border-0 cursor-pointer">
            Giu ${chon.length} ghe</button>`;
        t.querySelector("#giu").onclick = () => giuGhe([...dangChon]);
    }

    async function giuGhe(seatIds) {
        try {
            const don = await post(`/showtimes/${id}/holds`, { seatIds });
            location.hash = `#/thanh-toan/${don.bookingId}`;
        } catch (e) {
            // Backend da dung san cau day du: SeatsUnavailableException sinh ra
            //   "Cac ghe sau da co nguoi giu: D5, D6"
            // va BookingExceptionHandler dua no vao ApiError.message.
            //
            // Danh sach ghe KHONG den duoi dang co cau truc: ApiError chi co {code, message},
            // nen takenSeatLabels bi bo lai o tang web. Dung thang message la dung hon viec
            // tu tach chuoi roi ghep lai — no da la mot cau tieng Viet hoan chinh.
            //
            // api.js nem Error co gan them {code, status}. KHONG co truong "than".
            moHopThoai({
                tieuDe: e.code === "SEATS_UNAVAILABLE"
                    ? "Ghe vua co nguoi khac giu"
                    : "Khong giu duoc ghe",
                than: thoatHtml(e.message ?? "Ban chon lai ghe khac nhe."),
                nhan: "Chon lai",
                khiBam: () => {
                    dangChon.clear();
                    get(`/showtimes/${id}/seats`).then((moi) => {
                        danhSachGhe = moi; veLuoi(); veTomTat();
                    });
                },
            });
        }
    }

    return el;
}

export function huyBo() {
    if (ketNoiHienTai) { ketNoiHienTai.dong(); ketNoiHienTai = null; }
}
