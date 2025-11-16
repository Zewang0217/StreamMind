package org.zewang.common.dto.social_message;


import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 社交媒体消息
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/10 19:03
 */

/** Builder
 * 1. 自动生成建造者模式代码：自动创建 builder() 方法和相关构建逻辑
 * 2. 简化对象创建：避免编写冗长的构造函数或 setter 方法
 * 3. 提高可读性：通过链式调用方式创建对象，代码更清晰
 */

/**为什么使用record？
 * 1. 不可变性：record 默认是不可变的，线程安全
 * 2. 简洁性：自动生成构造函数、getter 方法、equals()、hashCode() 和 toString()
 * 3. 数据载体：专门设计用于表示不可变的数据载体类
 * 4. 减少样板代码：无需手动编写 getter、setter 等方法
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
