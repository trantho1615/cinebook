// Khung xuong dung hinh dang noi dung sap toi, de trang khong nhay khi du lieu ve.
// Chu "Dang tai..." khong cho biet sap co gi va lam trang giat hai lan.

const NHAP_NHAY = "animate-pulse bg-theNoi rounded";

export function luoiPhim(soThe = 8) {
    const el = document.createElement("div");
    el.className = "grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5";
    el.innerHTML = Array.from({ length: soThe }, () => `
      <div>
        <div class="${NHAP_NHAY} aspect-[2/3]"></div>
        <div class="${NHAP_NHAY} h-3.5 mt-2.5"></div>
        <div class="${NHAP_NHAY} h-3 w-3/5 mt-1.5"></div>
      </div>`).join("");
    return el;
}

export function khoiChu(soDong = 3) {
    const el = document.createElement("div");
    el.className = "flex flex-col gap-2";
    el.innerHTML = Array.from({ length: soDong },
        (_, i) => `<div class="${NHAP_NHAY} h-3.5" style="width:${100 - i * 12}%"></div>`).join("");
    return el;
}
