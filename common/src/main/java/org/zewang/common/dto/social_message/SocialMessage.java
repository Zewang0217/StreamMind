package org.zewang.common.dto.social_message;


import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: TODO
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/10 19:03
 */

@Builder // 作用: 创建对象
public record SocialMessage (
    String messageId, // 注意全局唯一性
    String source,
    String topic,
    String userId,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    LocalDateTime timestamp,
    String content,
    int interactionCount

) {}
