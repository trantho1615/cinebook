import { khoiTao } from "./router.js";
import { dungHeader } from "./ui/header.js";
import { datNguoiDung } from "./store.js";
import { token, get } from "./api.js";
import * as dangNhap from "./screens/dang-nhap.js";
import * as khongTimThay from "./screens/khong-tim-thay.js";

const dinhTuyen = { dangNhap, khongTimThay };

async function batDau() {
    document.getElementById("header").append(dungHeader());
    // Con token cu thi khoi phuc phien truoc khi ve man hinh dau tien, de header khong
    // nhay tu "Dang nhap" sang ten nguoi dung ngay truoc mat.
    if (token()) {
        try { datNguoiDung(await get("/auth/me")); } catch { /* token het han, coi nhu chua dang nhap */ }
    }
    khoiTao(dinhTuyen, document.getElementById("app"));
}

batDau();
