// Anh xa mot ghe tu API sang thuoc tinh hien thi.
//
// Ham THUAN, khong dung DOM: day la logic duy nhat cua giao dien dang test don vi duoc,
// va no la cho de sai nhat — bon trang thai, ba tin hieu, va nhan cho trinh doc man hinh.

const LOP = {
    AVAILABLE: "ghe ghe-trong",
    HELD:      "ghe ghe-khac",
    BOOKED:    "ghe ghe-ban",
};

export function dinhDangTien(so) {
    return new Intl.NumberFormat("vi-VN").format(so);
}

export function thuocTinhGhe(ghe, dangChon) {
    // Y dinh cua nguoi dung thang trang thai server tra ve: ho vua bam, va giao dien phai
    // phan hoi ngay chu khong doi mot vong mang.
    if (dangChon && ghe.status === "AVAILABLE") {
        return {
            lop: "ghe ghe-toi",
            chonDuoc: true,
            nhan: `Ghe ${ghe.label}, ban dang chon, ${dinhDangTien(ghe.price)} dong`,
        };
    }
    if (ghe.status === "HELD") {
        return { lop: LOP.HELD, chonDuoc: false, nhan: `Ghe ${ghe.label}, nguoi khac dang giu` };
    }
    if (ghe.status === "BOOKED") {
        return { lop: LOP.BOOKED, chonDuoc: false, nhan: `Ghe ${ghe.label}, da ban` };
    }
    return {
        lop: LOP.AVAILABLE,
        chonDuoc: true,
        nhan: `Ghe ${ghe.label}, con trong, ${dinhDangTien(ghe.price)} dong`,
    };
}
