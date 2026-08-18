package com.cinebook.identity.api;

import java.util.UUID;

/**
 * Be mat cong khai cua module identity cho viec kiem tra quyen so huu.
 *
 * Milestone 2 co y chua tao package identity.api vi chua module nao can. Gio
 * booking can kiem tra "don nay co phai cua nguoi goi khong", nen no ra doi
 * dung luc — dung nguyen tac chi tao khi co nguoi goi.
 */
public interface AccessControl {

    /**
     * Nem ForbiddenException neu nguoi goi khong phai chu so huu va cung khong phai ADMIN.
     * Nguoi goi duoc lay tu SecurityContext, khong truyen vao — truyen them tham so
     * chi tao co hoi truyen nham id cua nguoi khac.
     */
    void requireSelfOrAdmin(UUID ownerId);
}
