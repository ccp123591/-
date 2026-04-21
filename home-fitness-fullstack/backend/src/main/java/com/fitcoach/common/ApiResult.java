package com.fitcoach.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应结构
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResult<T> {
    private int code;
    private String message;
    private T data;
    private long timestamp = System.currentTimeMillis();

    public static final int CODE_OK = 0;
    public static final int CODE_FAIL = -1;

    public static <T> ApiResult<T> ok() {
        return new ApiResult<>(CODE_OK, "success", null, System.currentTimeMillis());
    }

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(CODE_OK, "success", data, System.currentTimeMillis());
    }

    public static <T> ApiResult<T> ok(T data, String message) {
        return new ApiResult<>(CODE_OK, message, data, System.currentTimeMillis());
    }

    public static <T> ApiResult<T> fail(String message) {
        return new ApiResult<>(CODE_FAIL, message, null, System.currentTimeMillis());
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, null, System.currentTimeMillis());
    }
}
