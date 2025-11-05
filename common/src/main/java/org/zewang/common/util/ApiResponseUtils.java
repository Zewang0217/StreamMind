package org.zewang.common.util;


import org.zewang.common.response.ApiResponse;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: API响应工具
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/05 21:08
 */

public class ApiResponseUtils {

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data);
    }

    public static <T> ApiResponse<T> success() {
        return ApiResponse.success();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.error(code, message);
    }

    public static <T> ApiResponse<T> error(org.zewang.common.exception.ErrorCode errorCode) {
        return ApiResponse.error(errorCode);
    }
}
