export function render() {
    const el = document.createElement("div");
    el.className = "max-w-5xl mx-auto px-4 py-24 text-center";
    el.innerHTML = `
      <div class="serif text-3xl">Khong tim thay trang</div>
      <p class="text-chuMo mt-3">Duong dan nay khong ton tai.</p>
      <a href="#/" class="inline-block mt-6 bg-nhan text-nen font-bold px-4 py-2 rounded-md no-underline">
        Ve trang chu</a>`;
    return el;
}
