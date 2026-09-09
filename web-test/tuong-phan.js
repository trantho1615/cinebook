// Kiem tuong phan WCAG AA mot lan, khong phai test chay mai.
// Chay:  node web-test/tuong-phan.js

const CAP = [
    ["chu tren nen",      "#EDEDF0", "#0B0B0D", 4.5],
    ["chu mo tren nen",   "#8A8A94", "#0B0B0D", 4.5],
    ["chu mo tren the",   "#8A8A94", "#131316", 4.5],
    ["chu mo tren the noi","#8A8A94", "#1A1A1F", 4.5],
    ["nhan tren nen",     "#E8B44A", "#0B0B0D", 4.5],
    ["nhan tren the",     "#E8B44A", "#131316", 4.5],
    ["nhan MAN HINH tren nen", "#787882", "#0B0B0D", 4.5],
    ["nen tren nhan",     "#1A1200", "#E8B44A", 4.5],
    ["loi tren the",      "#F87171", "#131316", 4.5],
    ["thanh cong tren the","#6FCF97", "#131316", 4.5],
    ["so ghe trong",      "#8C8C96", "#26262D", 4.5],
    ["so ghe nguoi khac", "#9A7F4D", "#1E1B14", 4.5],
    // Trang thai hover cua nut chinh. Mau nen doi tu --nhan sang --nhan-dam khi di chuot qua,
    // nen day la mot CAP MAU KHAC voi "nen tren nhan" o tren va phai duoc kiem rieng.
    ["chu nut tren nhan dam (hover)", "#0B0B0D", "#C9963A", 4.5],
];
// Ghe da ban khong co cap nao: no khong hien so. Xem ghi chu o Task 7.

const kenh = (v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4);
const doSang = (hex) => {
    const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map(kenh);
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};
const tiLe = (a, b) => {
    const [x, y] = [doSang(a), doSang(b)].sort((p, q) => q - p);
    return (x + 0.05) / (y + 0.05);
};

let hong = 0;
for (const [ten, truoc, sau, nguong] of CAP) {
    const t = tiLe(truoc, sau);
    const dat = t >= nguong;
    if (!dat) hong++;
    console.log(`${dat ? "OK  " : "HONG"}  ${ten.padEnd(22)} ${t.toFixed(2)}:1  (can ${nguong}:1)`);
}
process.exit(hong ? 1 : 0);
