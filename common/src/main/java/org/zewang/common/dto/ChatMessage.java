package org.zewang.common.dto;


import lombok.Getter;
import lombok.Setter;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 聊天消息结构
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/05 18:19
 */

@Getter
@Setter
public class ChatMessage {
    private String userId;
    private long timestamp;
    private String message;
}
