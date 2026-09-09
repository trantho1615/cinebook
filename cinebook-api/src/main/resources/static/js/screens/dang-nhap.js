import { dangNhap, dangNhapDemo, dangKy, get } from "../api.js";
import { datNguoiDung, layTinMotLan } from "../store.js";

export function render() {
    const el = document.createElement("div");
    el.className = "max-w-sm mx-auto px-4 py-16";
    el.innerHTML = `
      <div class="serif text-3xl mb-6">Dang nhap</div>
      <form id="f" class="flex flex-col gap-3">
        <input id="email" type="email" required placeholder="email@example.com"
               class="bg-the border border-vien text-chu px-3 py-2.5 rounded-md">
        <input id="mk" type="password" required placeholder="Mat khau"
               class="bg-the border border-vien text-chu px-3 py-2.5 rounded-md">
        <p id="loi" class="text-loi text-sm m-0 hidden"></p>
        <button type="submit" class="bg-nhan text-nen font-bold py-2.5 rounded-md border-0 cursor-pointer">
          Dang nhap</button>
      </form>
      <div class="flex items-center gap-3 my-5 text-chuMo text-xs">
        <div class="h-px bg-vien flex-1"></div>hoac<div class="h-px bg-vien flex-1"></div>
      </div>
      <button id="demo" class="w-full border border-vien text-chu py-2.5 rounded-md bg-transparent cursor-pointer">
        Dung tai khoan demo</button>
      <p class="text-chuMo text-sm mt-6">Chua co tai khoan?
        <button id="tao" class="text-nhan bg-transparent border-0 cursor-pointer p-0">Tao tai khoan</button></p>`;

    const loi = el.querySelector("#loi");
    function baoLoi(tin) { loi.textContent = tin; loi.classList.remove("hidden"); }

    // Phien het han giua chung mot luong thi api.js da dat lai mot cau giai thich. Khong co
    // no, nguoi dung bi bat len day ma khong hieu vi sao.
    const tinCu = layTinMotLan();
    if (tinCu) baoLoi(tinCu);

    async function vaoUngDung() {
        datNguoiDung(await get("/auth/me"));
        location.hash = "#/";
    }

    el.querySelector("#f").onsubmit = async (e) => {
        e.preventDefault();
        try {
            await dangNhap(el.querySelector("#email").value, el.querySelector("#mk").value);
            await vaoUngDung();
        } catch { baoLoi("Email hoac mat khau khong dung."); }
    };
    el.querySelector("#demo").onclick = async () => {
        try { await dangNhapDemo(); await vaoUngDung(); }
        catch { baoLoi("Khong tao duoc tai khoan demo."); }
    };
    el.querySelector("#tao").onclick = async () => {
        const email = el.querySelector("#email").value;
        const mk = el.querySelector("#mk").value;
        if (!email || !mk) return baoLoi("Nhap email va mat khau truoc.");
        try { await dangKy(email, mk, "Nguoi dung moi", "0900000000"); await vaoUngDung(); }
        catch { baoLoi("Khong tao duoc tai khoan. Email co the da ton tai."); }
    };
    return el;
}
