package org.zewang.common.constant;


/**
 * @author "Zewang"
 * @version 1.0
 * @description: Kafka 相关常量定义
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/05 20:42
 */

public class KafkaConstants {

    // Topic名称常量
    public static final String CHAT_MESSAGES_TOPIC = "chat-messages";
    public static final String SENTIMENT_SCORES_TOPIC = "sentiment-scores";
    public static final String WARNING_ALERTS_TOPIC = "warning-alerts";

    // 默认配置值
    public static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";
    public static final String STRING_SERIALIZER = "org.apache.kafka.common.serialization.StringSerializer";
    public static final String STRING_DESERIALIZER = "org.apache.kafka.common.serialization.StringDeserializer";
    public static final String JSON_SERIALIZER = "org.springframework.kafka.support.serializer.JsonSerializer";
    public static final String JSON_DESERIALIZER = "org.springframework.kafka.support.serializer.JsonDeserializer";

    private KafkaConstants() {} // 私有化构造函数，防止实例化
}
