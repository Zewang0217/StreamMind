// src/main/java/org/zewang/common/exception/ErrorCode.java
package org.zewang.common.exception;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 错误码枚举类
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/05
 */
public enum ErrorCode {
    // 系统级错误码 (1000-1999)
    SYSTEM_ERROR("1000", "系统内部错误"),
    PARAMETER_INVALID("1001", "参数无效"),
    RESOURCE_NOT_FOUND("1002", "资源未找到"),

    // Kafka相关错误码 (2000-2999)
    KAFKA_SEND_ERROR("2000", "Kafka消息发送失败"),
    KAFKA_CONFIG_ERROR("2001", "Kafka配置错误"),
    KAFKA_SERIALIZATION_ERROR("2002", "Kafka序列化失败"),

    // 业务相关错误码 (3000-3999)
    CHAT_MESSAGE_INVALID("3000", "聊天消息无效"),
    SENTIMENT_ANALYSIS_ERROR("3001", "情感分析失败"),
    ALERT_GENERATION_ERROR("3002", "预警生成失败");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
