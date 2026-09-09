import { test } from "node:test";
import assert from "node:assert/strict";
import { thuocTinhGhe } from "../cinebook-api/src/main/resources/static/js/ghe.js";

const A5 = { seatId: "s1", label: "A5", seatType: "STANDARD", price: 90000, status: "AVAILABLE" };

test("ghe trong: chon duoc, nhan doc duoc thanh tieng", () => {
    const t = thuocTinhGhe(A5, false);
    assert.equal(t.lop, "ghe ghe-trong");
    assert.equal(t.chonDuoc, true);
    assert.equal(t.nhan, "Ghe A5, con trong, 90.000 dong");
});

test("ghe dang chon: lop khac, nhan noi ro dang chon", () => {
    const t = thuocTinhGhe(A5, true);
    assert.equal(t.lop, "ghe ghe-toi");
    assert.equal(t.chonDuoc, true);
    assert.equal(t.nhan, "Ghe A5, ban dang chon, 90.000 dong");
});

test("nguoi khac giu: khong chon duoc", () => {
    const t = thuocTinhGhe({ ...A5, status: "HELD" }, false);
    assert.equal(t.lop, "ghe ghe-khac");
    assert.equal(t.chonDuoc, false);
    assert.equal(t.nhan, "Ghe A5, nguoi khac dang giu");
});

test("da ban: khong chon duoc, khong noi gia", () => {
    const t = thuocTinhGhe({ ...A5, status: "BOOKED" }, false);
    assert.equal(t.lop, "ghe ghe-ban");
    assert.equal(t.chonDuoc, false);
    assert.equal(t.nhan, "Ghe A5, da ban");
});

test("y dinh cua nguoi dung thang khi server con noi AVAILABLE", () => {
    // Nguoi dung vua bam, server chua kip tra ve. Hien thi phai theo y dinh cua ho.
    const t = thuocTinhGhe(A5, true);
    assert.equal(t.lop, "ghe ghe-toi");
    assert.equal(t.chonDuoc, true);
});

test("server thang khi ghe bi nguoi khac lay mat trong luc dang chon", () => {
    // Cuoc dua that cua ung dung nay: nguoi dung bam mot ghe, va giua luc do nguoi khac
    // giu hoac mua mat. Lua chon cu con trong may KHONG duoc de len trang thai that —
    // neu de, nguoi dung thay ghe van sang mau minh chon va bam thanh toan vao mot ghe
    // khong con la cua ho.
    const dangGiu = thuocTinhGhe({ ...A5, status: "HELD" }, true);
    assert.equal(dangGiu.lop, "ghe ghe-khac");
    assert.equal(dangGiu.chonDuoc, false);
    assert.equal(dangGiu.nhan, "Ghe A5, nguoi khac dang giu");

    const daBan = thuocTinhGhe({ ...A5, status: "BOOKED" }, true);
    assert.equal(daBan.lop, "ghe ghe-ban");
    assert.equal(daBan.chonDuoc, false);
    assert.equal(daBan.nhan, "Ghe A5, da ban");
});
