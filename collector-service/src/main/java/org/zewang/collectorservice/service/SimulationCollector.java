package org.zewang.collectorservice.service;


import com.opencsv.bean.CsvToBeanBuilder;
import jakarta.annotation.PostConstruct;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.zewang.common.constant.KafkaConstants;
import org.zewang.common.dto.social_message.SocialMessage;
import org.zewang.common.dto.social_message.SocialMessageCsvDto;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 每隔10ms一次定时任务，读取10条记录并发送到kafka主题
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/10 19:08
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class SimulationCollector {

    private final KafkaTemplate<String, SocialMessage> kafkaTemplate;

    private String topic = KafkaConstants.SOCIAL_MESSAGES;
    private List<SocialMessageCsvDto> csvData;
    private int currentIndex = 0;

    @PostConstruct
    public void loadCsvData() throws Exception {
        var resource = new ClassPathResource("csv/sample-data.csv"); // var类型，自动推断类型
        try (var reader = new InputStreamReader(resource.getInputStream())) {
            csvData = new CsvToBeanBuilder<SocialMessageCsvDto>(reader)
                .withType(SocialMessageCsvDto.class)
                .build()
                .parse();
        }
    }

    @Scheduled(fixedDelay = 10)
    public void sendBatchMessages() {
        if (csvData == null || csvData.isEmpty()) return;

        Random random = new Random();

        for (int i = 0; i < 10; i++) {
            if (currentIndex >= csvData.size()) {
                currentIndex = 0;
            }

            SocialMessageCsvDto dto = csvData.get(currentIndex++);
            SocialMessage message = SocialMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .source(random.nextBoolean() ? "weibo" : "zhihu")
                .topic(dto.getTopic())
                .userId(dto.getUserId())
                .timestamp(LocalDateTime.now())
                .content(dto.getContent())
                .interactionCount(Integer.parseInt(dto.getInteractionCount()))
                .build();

            kafkaTemplate.send(topic, message.messageId(), message);
            log.info(" 发送消息 ：{}", message);

        }
    }


}
