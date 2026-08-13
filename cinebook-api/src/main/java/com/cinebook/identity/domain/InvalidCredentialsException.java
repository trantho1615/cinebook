package com.cinebook.identity.domain;

/**
 * Mot thong bao duy nhat cho ca "email khong ton tai" lan "sai mat khau" —
 * do la ly do lop nay khong nhan tham so. Phan biet hai truong hop la de lo
 * danh sach email da dang ky.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Email hoac mat khau khong dung");
    }
}
