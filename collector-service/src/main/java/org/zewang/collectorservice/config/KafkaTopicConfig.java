package org.zewang.collectorservice.config;


import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;
import org.zewang.common.constant.KafkaConstants;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 创建kafka topic
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/11 18:16
 */

@Configuration
public class KafkaTopicConfig {
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    @Bean
    public NewTopic socialMessagesTopic() {
        return new NewTopic(KafkaConstants.SOCIAL_MESSAGES_TOPIC, 3, (short) 1);
    }

    @Bean
    public NewTopic analyzedStreamTopic() {
        return new NewTopic(KafkaConstants.ANALYZED_STREAM_TOPIC, 3, (short) 1);
    }

    @Bean
    public NewTopic alertEventsTopic() {
        return new NewTopic(KafkaConstants.ALERT_EVENTS_TOPIC, 3, (short) 1);
    }

}
