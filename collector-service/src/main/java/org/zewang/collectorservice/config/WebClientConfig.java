package org.zewang.collectorservice.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: WebClient配置类
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/18 18:13
 */

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
            // 设置最大内存大小
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024)) // 1MB
            .build();
    }
}
