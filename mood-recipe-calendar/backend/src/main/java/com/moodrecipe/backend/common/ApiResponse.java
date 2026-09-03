package com.moodrecipe.backend.common;

import lombok.Data;

@Data
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> res = new ApiResponse<>();
        res.code = 0;
        res.message = "success";
        res.data = data;
        return res;
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> res = new ApiResponse<>();
        res.code = code;
        res.message = message;
        return res;
    }

    public static <T> ApiResponse<T> error(String message) {
        return error(500, message);
    }
}
