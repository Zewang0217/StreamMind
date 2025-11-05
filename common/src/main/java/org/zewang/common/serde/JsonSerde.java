package org.zewang.common.serde;


import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 通用 JSON 序列化/反序列化类
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/05 19:24
 */

public class JsonSerde<T> implements Serializer<T>, Deserializer<T> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Class<T> targetType;

    public JsonSerde(Class<T> targetType) {
        this.targetType = targetType;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
    }


    @Override
    public byte[] serialize(String topic, T data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new RuntimeException("错误序列化 JSON 消息", e);
        }
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.readValue(data, targetType);
        } catch (IOException e) {
            throw new RuntimeException("Error deserializing JSON message", e);
        }
    }

    @Override
    public void close() {

    }
}
