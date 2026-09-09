import { test } from "node:test";
import assert from "node:assert/strict";
import { phanGiai, CAN_DANG_NHAP } from "../cinebook-api/src/main/resources/static/js/router.js";

test("hash rong tra ve man hinh phim dang chieu", () => {
    assert.deepEqual(phanGiai(""), { ten: "phim", thamSo: {} });
    assert.deepEqual(phanGiai("#/"), { ten: "phim", thamSo: {} });
});

test("tuyen co tham so tra ve dung id", () => {
    assert.deepEqual(phanGiai("#/phim/abc-123"), { ten: "chiTietPhim", thamSo: { id: "abc-123" } });
    assert.deepEqual(phanGiai("#/suat/s-9/ghe"), { ten: "soDoGhe", thamSo: { id: "s-9" } });
    assert.deepEqual(phanGiai("#/ve/b-7"), { ten: "chiTietVe", thamSo: { id: "b-7" } });
    assert.deepEqual(phanGiai("#/thanh-toan/b-42"), { ten: "thanhToan", thamSo: { id: "b-42" } });
});

test("tuyen khong tham so", () => {
    assert.deepEqual(phanGiai("#/ve-cua-toi"), { ten: "veCuaToi", thamSo: {} });
    assert.deepEqual(phanGiai("#/dang-nhap"), { ten: "dangNhap", thamSo: {} });
});

test("hash khong khop tra ve 404 chu khong nem loi", () => {
    assert.deepEqual(phanGiai("#/khong-co-tuyen-nay"), { ten: "khongTimThay", thamSo: {} });
    assert.deepEqual(phanGiai("#/phim"), { ten: "khongTimThay", thamSo: {} });
});

test("CAN_DANG_NHAP chi chan cac man hinh can biet danh tinh nguoi dung", () => {
    for (const ten of ["veCuaToi", "chiTietVe", "thanhToan"]) {
        assert.ok(CAN_DANG_NHAP.has(ten), `${ten} phai can dang nhap`);
    }
    for (const ten of ["phim", "chiTietPhim", "soDoGhe", "dangNhap"]) {
        assert.ok(!CAN_DANG_NHAP.has(ten), `${ten} khong duoc can dang nhap`);
    }
});
