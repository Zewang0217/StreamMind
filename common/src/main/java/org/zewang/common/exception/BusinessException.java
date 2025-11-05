package org.zewang.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final String code;
    private final Object[] args; // 错误参数

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.args = null;
    }

    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.args = args;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
        this.args = null;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
        this.args = args;
    }
}
