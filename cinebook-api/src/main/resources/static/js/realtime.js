// Boc client STOMP TU VIET cua du an (js/stomp.js) lai, va them hai thu no khong co:
// tu ket noi lai, va BAO RA khi mat ket noi.
//
// js/stomp.js la mot ES module 40 dong xuat ketNoi(dich, khiCoTin) -> WebSocket. No KHONG
// phai thu vien ben ngoai va KHONG co bien toan cuc StompJs: nap no bang <script> thuong
// se nem "SyntaxError: Unexpected token 'export'".
//
// Giao dien cu im lang hien so do ghe cu khi WebSocket dut. Do la kieu lua nguoi dung te
// nhat — man hinh trong nhu binh thuong trong khi du lieu da cu.

import { ketNoi as moKenh } from "./stomp.js";

const CHO_TRUOC_KHI_THU_LAI = 3000;

export function ketNoi(showtimeId, { khiGheDoi, khiMatKetNoi, khiNoiLai }) {
    let ws = null;
    let hen = null;
    let daDong = false;

    function mo() {
        if (daDong) return;
        ws = moKenh(`/topic/showtimes/${showtimeId}`, khiGheDoi);
        ws.addEventListener("open", () => khiNoiLai());
        ws.addEventListener("close", () => {
            // Ta chu dong dong khi roi man hinh: do khong phai su co, khong duoc bao.
            if (daDong) return;
            khiMatKetNoi();
            hen = setTimeout(mo, CHO_TRUOC_KHI_THU_LAI);
        });
        // Khong bat "error" rieng: moi loi WebSocket deu keo theo "close", nen bat ca hai
        // la bao hai lan cho cung mot su co.
    }

    mo();
    return {
        dong() {
            daDong = true;
            if (hen) { clearTimeout(hen); hen = null; }
            if (ws) ws.close();
        },
    };
}
