package org.zewang.producer.config;


import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.zewang.common.constant.KafkaConstants;
import org.zewang.common.dto.ChatMessage;
import org.zewang.common.dto.WarningAlert;
import org.zewang.common.serde.JsonSerde;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: kafka生产者配置类
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/05 18:41
 */

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public Map<String, Object> producerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // 调优配置
        props.put(ProducerConfig.ACKS_CONFIG, "1"); // 1表示只需要leader确认，提高吞吐量
        props.put(ProducerConfig.RETRIES_CONFIG, 3); // 重试次数
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 批处理大小(16KB)
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5); // 批处理延迟时间(5ms)
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 32MB发送缓冲区
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy"); // 启用压缩
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5); // 每个连接最大请求数

        return props;
    }

    @Bean
    public ProducerFactory<String, ChatMessage> producerFactory() {
        // 移除事务相关配置，直接返回默认的ProducerFactory
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, ChatMessage> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }



    // 创建 chat-messages topic  => 在配置文件启用自动创建topic
//    @Bean
//    public NewTopic chatMessageTopic() {
//        return new NewTopic(KafkaConstants.CHAT_MESSAGES_TOPIC, 3, (short)1);
//    }

}
