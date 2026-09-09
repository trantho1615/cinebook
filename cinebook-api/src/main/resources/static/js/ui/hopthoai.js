// Hop thoai CHAN, khong phai toast.
//
// Dung cho mot tinh huong duy nhat: nguoi dung vua MAT thu gi do (het gio giu cho).
// Toast troi qua trong ba giay, va mot nguoi vua mat cho ngoi khong duoc biet dieu do
// bang mot dong chu tu bien mat.
//
// HOP DONG: tieuDe va than duoc noi thang vao innerHTML, KHONG tu thoat o day. Moi noi
// goi da tu thoatHtml() gia tri server truoc khi truyen vao (xem so-do-ghe.js, thanh-toan.js).
// Thoat lai o day se bien "&amp;" that su thanh "&amp;amp;" hien thi cho nguoi dung — thoat
// hai lan sai y hon khong thoat lan nao. Neu them mot noi goi moi, noi do phai tu thoat.

export function moHopThoai({ tieuDe, than, nhan, khiBam }) {
    const nen = document.createElement("div");
    nen.className = "fixed inset-0 bg-black/70 flex items-center justify-center z-50 px-4";
    nen.innerHTML = `
      <div role="alertdialog" aria-modal="true"
           class="bg-the border border-vien rounded-xl p-6 max-w-sm w-full">
        <div class="serif text-2xl">${tieuDe}</div>
        <p class="text-chuMo mt-3 leading-relaxed">${than}</p>
        <button class="mt-5 w-full bg-nhan text-nen font-bold py-2.5 rounded-md border-0 cursor-pointer">
          ${nhan}</button>
      </div>`;
    // aria-label dat qua setAttribute, khong noi vao chuoi HTML: tieuDe co the chua dau
    // nhay va lam gay markup neu noi thang vao thuoc tinh trong template string.
    nen.querySelector('[role="alertdialog"]').setAttribute("aria-label", tieuDe);
    const nut = nen.querySelector("button");
    nut.onclick = () => { nen.remove(); khiBam(); };
    document.body.append(nen);
    nut.focus();
}
