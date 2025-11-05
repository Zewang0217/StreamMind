package org.zewang.common.response;


import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import org.zewang.common.exception.ErrorCode;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 统一API响应结构
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/05 20:58
 */

@Getter
@Setter
public class ApiResponse<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private String code;
    private String message;
    private T data; // 相应数据
    private boolean success;
    private Long timestamp = System.currentTimeMillis();

    public ApiResponse(String code, String message, T data, boolean success) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.success = success;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("0", "success", data, true);
    }

    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null, false);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null, false);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null, false);
    }
}
