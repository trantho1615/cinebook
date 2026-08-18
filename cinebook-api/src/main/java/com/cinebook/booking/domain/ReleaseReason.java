package com.cinebook.booking.domain;

public enum ReleaseReason {
    /** Nguoi den sau don cho cua hold da het han. */
    TAKEN_OVER,
    /** Sweeper chay nen don. */
    SWEPT,
    /** Nguoi dung tu huy. */
    USER_CANCELLED,
    /** Thanh toan that bai. */
    PAYMENT_FAILED
}
