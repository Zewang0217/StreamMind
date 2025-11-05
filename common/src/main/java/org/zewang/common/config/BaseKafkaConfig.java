package org.zewang.common.config;


import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: Kafka基础配置类，共各模块继承使用
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/05 19:21
 */

@Configuration
public class BaseKafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    protected String bootstrapServers;

    // 创建基础的生产者配置
    protected Map<String, Object> createBaseProducerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", bootstrapServers);
        return props;
    }

    // 创建基础的消费者配置
    protected Map<String, Object> createBaseConsumerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.springframework.kafka.support.serializer.JsonDeserializer");
        return props;
    }

}
