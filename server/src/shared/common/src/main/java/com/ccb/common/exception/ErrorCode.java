package com.ccb.common.exception;

public final class ErrorCode {
    public static final int BAD_REQUEST = 40000;
    public static final int UNAUTHORIZED = 40100;
    public static final int FORBIDDEN = 40300;
    public static final int CONFLICT = 40900;
    public static final int INTERNAL_ERROR = 50000;

    private ErrorCode() {
    }
}
