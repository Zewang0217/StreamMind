package org.zewang.collectorservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.zewang.collectorservice.service.interfaces.AIScoringService;
import org.zewang.common.dto.social_message.SocialMessage;
import org.zewang.common.exception.BusinessException;
import org.zewang.common.exception.ErrorCode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * 负责调用AI服务进行评分和分类的实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIScoringServiceImpl implements AIScoringService {

    // 火山方舟API配置
    // 直接读取环境变量，如果环境变量不存在则使用application.yml中的配置
    @Value("${VOLCENGINE_API_KEY:${volcengine.api.key:}}")
    private String volcengineApiKey;
    @Value("${VOLCENGINE_API_SECRET:${volcengine.api.secret:}}")
    private String volcengineApiSecret;
    @Value("${VOLCENGINE_API_ENDPOINT:${volcengine.api.endpoint:https://ark.cn-beijing.volces.com/api/v3/chat/completions}}")
    private String volcengineApiEndpoint;
    @Value("${VOLCENGINE_MODEL_ID:${volcengine.model.id:doubao-seed-1-6-flash-250828}}")
    private String modelId;

    @Override
    public String scoreArticles(List<SocialMessage> articles) {
        // 构建批量评分提示词
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("请对以下技术文章进行评分、分类和关键词提取：\n");
        promptBuilder.append("1. 评分：对每篇文章质量进行1-10分制评分\n");
        promptBuilder.append("2. 分类：从[人工智能,后端,前端,产品,开源项目,移动开发,区块链,网络安全,DevOps,云计算]中选择\n");
        promptBuilder.append("3. 关键词：提取3-5个核心关键词\n");
        promptBuilder.append("4. 严格按照以下JSON格式输出，不要添加其他内容：\n");
        promptBuilder.append("[\n  {\n    \"article_identifier\": \"文章的messageId\",\n    \"score\": 8,\n    \"category\": \"人工智能\",\n    \"keywords\": [\"机器学习\", \"深度学习\", \"神经网络\"]\n  }\n]\n\n");
        promptBuilder.append("文章列表：\n\n");

        // 添加文章内容到提示词
        for (int i = 0; i < articles.size(); i++) {
            SocialMessage message = articles.get(i);
            promptBuilder.append("文章 " + (i + 1) + " (messageId: " + message.messageId() + "):\n");
            promptBuilder.append("标题和描述: " + message.content() + "\n\n");
        }

        try {
            // 调用火山方舟API进行评分和分类
            log.info("调用火山方舟{}模型对{}篇文章进行评分和分类", modelId, articles.size());
            return callVolcengineApi(promptBuilder.toString());
        } catch (Exception e) {
            log.error("调用AI服务评分和分类时出错: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.AI_CALL_ERROR);
        }
    }

    /**
     * 调用火山引擎AI
     * @param prompt 提示词
     * @return 响应结果
     */
    private String callVolcengineApi(String prompt) throws IOException, InterruptedException {
        // 如果没有配置API密钥，返回模拟数据
        if(volcengineApiKey == null || volcengineApiKey.isEmpty()) {
            log.warn("没有配置火山方舟API密钥，返回模拟数据");
            return generateMockResponse();
        }
        HttpResponse<String> response = null;

        try {
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelId);

            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", "你是一个专业的技术内容评估助手，擅长对文章进行评分、分类和关键词提取。");
            messages.add(systemMessage);

            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messages.add(userMessage);

            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.0);

            // 转为json
            String jsonBody = new ObjectMapper().writeValueAsString(requestBody);

            // 创建HTTP客户端和请求
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(volcengineApiEndpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + volcengineApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            // 发送请求并获取响应
            response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("API调用失败：" + response.statusCode() + "-" + response.body());
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_CALL_ERROR);
        }

        return response.body();
    }

    /**
     * 生成模拟响应数据（用于开发测试）
     */
    private String generateMockResponse() {
        StringBuilder mockJson = new StringBuilder("[");
        Random random = new Random();
        String[] categories = {"人工智能", "后端", "前端", "产品", "开源项目", "移动开发", "区块链", "网络安全", "DevOps", "云计算"};
        
        // 生成模拟评分数据
        mockJson.append("\n  {")
               .append("\n    \"article_identifier\": \"mock-1\",")
               .append("\n    \"score\": 8,")
               .append("\n    \"category\": \"人工智能\",")
               .append("\n    \"keywords\": [\"机器学习\", \"深度学习\", \"神经网络\"]")
               .append("\n  },")
               .append("\n  {")
               .append("\n    \"article_identifier\": \"mock-2\",")
               .append("\n    \"score\": 7,")
               .append("\n    \"category\": \"后端\",")
               .append("\n    \"keywords\": [\"微服务\", \"Spring Boot\", \"性能优化\"]")
               .append("\n  ")
               .append("\n]");
        
        return mockJson.toString();
    }
}
