// src/main/java/org/zewang/common/serde/JsonSerde.java
package org.zewang.common.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.Serde;

import java.io.IOException;
import java.util.Map;

public class JsonSerde<T> implements Serde<T> { // 实现 Serde 接口

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Class<T> targetType;
    private final JsonSerializer<T> serializer;
    private final JsonDeserializer<T> deserializer;

    public JsonSerde(Class<T> targetType) {
        this.targetType = targetType;
        this.serializer = new JsonSerializer<>();
        this.deserializer = new JsonDeserializer<>(targetType);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // 可以在这里配置 serializer 和 deserializer
        serializer.configure(configs, isKey);
        deserializer.configure(configs, isKey);
    }

    @Override
    public void close() {
        serializer.close();
        deserializer.close();
    }

    @Override
    public Serializer<T> serializer() {
        return serializer;
    }

    @Override
    public Deserializer<T> deserializer() {
        return deserializer;
    }

    // 内部 JsonSerializer 类
    private class JsonSerializer<T> implements Serializer<T> {
        @Override
        public void configure(Map<String, ?> configs, boolean isKey) {
            // 配置逻辑（如果需要）
        }

        @Override
        public byte[] serialize(String topic, T data) {
            if (data == null) {
                return null;
            }
            try {
                return objectMapper.writeValueAsBytes(data);
            } catch (Exception e) {
                throw new RuntimeException("Error serializing JSON message for topic: " + topic +
                    ", data type: " + data.getClass().getName() +
                    ", data: " + data, e);
            }
        }

        @Override
        public void close() {
            // 清理资源（如果需要）
        }
    }

    private class JsonDeserializer<T> implements Deserializer<T> {
        private final Class<T> targetType;

        public JsonDeserializer(Class<T> targetType) {
            this.targetType = targetType;
        }

        @Override
        public void configure(Map<String, ?> configs, boolean isKey) {
            // 配置逻辑（如果需要）
        }

        @Override
        public T deserialize(String topic, byte[] data) {
            if (data == null) {
                return null;
            }
            try {
                return objectMapper.readValue(data, targetType);
            } catch (IOException e) {
                throw new RuntimeException("Error deserializing JSON message for topic: " + topic +
                    ", target type: " + targetType.getName() +
                    ", data length: " + data.length, e);
            }
        }

        @Override
        public void close() {
            // 清理资源（如果需要）
        }
    }
}
