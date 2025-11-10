package org.zewang.streamanalyzer.processor;


import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.streams.processor.Cancellable;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zewang.common.dto.analyzer.AnalyzedMessage;
import org.zewang.common.dto.social_message.SocialMessage;
import org.zewang.streamanalyzer.service.MockAIService;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: Kafka Stream 拓扑结构
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/10 22:18
 */


public class BatchAIAnalysisProcessor implements
    Processor<String, SocialMessage, String, AnalyzedMessage> {
    private static final Logger log = LoggerFactory.getLogger(BatchAIAnalysisProcessor.class);
    private static final int BATCH_SIZE = 50;
    private static final Duration FLUST_INTERVAL = Duration.ofSeconds(10);

    private ProcessorContext context;
    private List<SocialMessage> buffer;
    private final MockAIService mockAiService = new MockAIService(); // TODO: 改为依赖注入

    private Cancellable schedule; // 定时任务

    @Override
    public void init(ProcessorContext context) {
        this.context = context;

        this.buffer = new ArrayList<>(BATCH_SIZE);

        this.schedule = context.schedule(
            Duration.ofMillis(1000),
            PunctuationType.WALL_CLOCK_TIME,
            this::flush
        );

    }

    @Override
    public void process(Record<String, SocialMessage> record) {
        SocialMessage message = record.value();
        if (message.content() == null || message.content().isEmpty()) {
            return;
        }

        buffer.add(message);

        if (buffer.size() >= BATCH_SIZE) {
            flush();
        }
    }

    public void close() {
        // 1. 强制flush剩余消息
        if (!buffer.isEmpty()) {
            log.info("关闭Processor，强制flush剩余{}消息", buffer.size());
            flush();
        }

        // 2. 取消定时器
        if (schedule != null) {
            schedule.cancel();
        }

        // 3. 关闭外部链接，这里没有

        // 4. 释放内存
        buffer.clear();
    }

    private void flush(long timestamp) {
        flush();
    }

    private void flush() {
        if (buffer.isEmpty()) {
            return;
        }

        log.debug("Processor刷新flush了{}条消息", buffer.size());
        try {
            List<AnalyzedMessage> results = mockAiService.analyzeBatch(buffer);
            for (AnalyzedMessage result : results) {
                context.forward(new Record<>(result.topic(), result, context.currentStreamTimeMs()));
            }
        } catch (Exception e) {
            log.error("AI批量分析时出现错误{}", e.getMessage());
            // TODO: 降级逻辑
        } finally {
            buffer.clear();
        }
    }

}
