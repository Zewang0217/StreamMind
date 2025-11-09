好的，我已经阅读并理解了您提供的所有文档。这是为您整理的开发文档，旨在让AI快速理解您的项目目标、当前阶段，并能提供针对性的代码建议。

---

### **项目名称：基于 Kafka Streams 的实时聊天情感分析与预警系统**

**项目概述:**
本项目旨在构建一个实时数据处理系统，使用 Spring Boot、Kafka、Kafka Streams 和 AI（如 Gemini）技术栈，对聊天消息进行实时情感分析、计算聚合指标（如平均情感得分），并能在特定条件下（如情绪持续低落）触发预警。项目将从模拟数据入手，逐步过渡到更复杂的数据源和功能。

**核心技术栈:**
*   Spring Boot (Producer, Consumer, Web Server)
*   Apache Kafka (消息队列)
*   Kafka Streams (实时流处理)
*   Google AI SDK / REST API (如 Gemini, 用于情感分析和回复建议)
*   WebSocket (可选，用于 V2.0 实时交互)
*   MySQL / JPA (可选，用于预警持久化)

**项目结构:**
```text
parent-project-|
               -common (共享实体类、配置)
               |
               -consumer (消费处理、Web展示)
               |
               -producer (数据生产)
               |
               -stream (Kafka Streams 处理逻辑)
```

---

### **开发阶段**

#### **阶段一 (V1.0): 模拟聊天记录室 - 基础流程打通**

*   **目标:** 搭建基础框架，使用模拟数据跑通从消息生产到 AI 分析再到预警计算的完整 Kafka Streams 流程。
*   **当前状态:** (假设为当前阶段) 您正在学习并准备实现这个基础版本。
*   **组件与任务:**

    *   **P1: 数据采集与生产 (Producer)**
        *   **目标:** 持续向 Kafka 发送模拟聊天消息。
        *   **Topic:** `chat-messages`
        *   **消息结构 (JSON Value):**
            ```json
            {
              "userId": "U123",
              "timestamp": 1700000000000,
              "message": "今天天气真不错，心情也很好！"
            }
            ```
        *   **Key:** `userId` (确保同一用户消息路由到同一分区)
        *   **实现:** 使用 `@Scheduled` 定时任务，模拟高频发送。
        *   **代码建议:** 配置 `KafkaTemplate`，设置 JSON 序列化器 (Serde)。创建一个 `ChatMessage` POJO 类放在 `common` 模块。

    *   **P2: 实时情感分析 (Stream Processor)**
        *   **目标:** 订阅 `chat-messages`，调用 AI API 分析情感，输出带情感得分的消息。
        *   **输入 Topic:** `chat-messages`
        *   **输出 Topic:** `sentiment-scores`
        *   **消息结构 (JSON Value):**
            ```json
            {
              "userId": "U123",
              "timestamp": 1700000000000,
              "message": "今天天气真不错，心情也很好！",
              "sentimentScore": 0.85,
              "sentimentLabel": "Positive"
            }
            ```
        *   **实现:** 使用 `KStream`，通过 `mapValues` 调用 Gemini API (注意同步调用可能成为瓶颈，考虑异步或批量优化)。配置 JSON Serde。
        *   **代码建议:** 创建 `SentimentScore` POJO。实现一个 `SentimentAnalysisService` 封装 AI API 调用逻辑。

    *   **P3: 实时预警计算 (Stream Processor)**
        *   **目标:** 订阅 `sentiment-scores`，计算时间窗口内的平均情感得分，过滤出低分预警。
        *   **输入 Topic:** `sentiment-scores`
        *   **输出 Topic:** `warning-alerts`
        *   **窗口:** Hopping Time Window (例如: 窗口大小 60 秒, 跳跃间隔 30 秒)
        *   **聚合:** 基于 `userId` 分组，计算窗口内 `sentimentScore` 的平均值。
        *   **过滤:** 平均得分低于阈值 (如 -0.5) 时输出预警。
        *   **消息结构 (JSON Value):**
            ```json
            {
              "userId": "U123",
              "windowEnd": 1700000060000,
              "averageScore": -0.72,
              "alertMessage": "用户情绪持续低落"
            }
            ```
        *   **实现:** 使用 `groupByKey` -> `windowedBy` -> `aggregate` -> `filter`。
        *   **代码建议:** 配置 `TimeWindows` 和 `Initializers`/`Aggregators`。

    *   **P4: 结果展示与持久化 (Consumer & Web)**
        *   **目标:** 消费预警消息并存入数据库；消费情感得分消息并展示。
        *   **输入 Topic:** `warning-alerts`, `sentiment-scores`
        *   **实现:** 使用 `@KafkaListener` 消费 `warning-alerts`，通过 JPA 存入 `alert_table`。消费 `sentiment-scores` 用于前端展示 (可选，可先打印日志)。
        *   **代码建议:** 配置 `@KafkaListener`，创建 `Alert` 实体和 Repository。

---

#### **阶段二 (V1.5): 数据源升级 - 历史人物性格总结**

*   **目标:** 将数据源从模拟数据替换为批量导入的真实聊天记录（如脱敏后的微信记录），并增加对历史数据的分析，实现人物性格总结功能。
*   **当前状态:** 待完成阶段一后进入。
*   **主要任务:**
    *   **数据预处理:** 设计工具或脚本将微信等平台导出的聊天记录转换为 `chat-messages` 格式。
    *   **批量导入:** 将处理后的历史数据导入 Kafka `chat-messages` Topic。
    *   **扩展 P2/P3:** 在 P2/P3 的流处理中，除了实时预警，还维护一个更长期的用户画像状态 (State Store)，用于总结历史性格特征。
    *   **P5: 历史分析 (Consumer/Service):** 订阅处理后的数据，或直接查询 State Store，实现“对一段时间内的人物特征进行总结”。
*   **代码建议:** 重点在于 `Kafka Streams` 的 `GlobalKTable` 或 `KTable` 用于状态管理，以及查询 State Store 的服务端点。

---

#### **阶段三 (V2.0): 实时交互 - "Crush 聊天建议" 功能**

*   **目标:** 实现一个实时交互系统，当用户与特定对象（如 Crush）聊天时，系统能实时分析对方消息并提供建议。
*   **当前状态:** 待完成阶段二后进入。
*   **主要任务:**
    *   **P1 (Client):** 开发一个客户端（如简单网页），通过 WebSocket 或 HTTP 将用户与 Crush 的实时聊天消息发送给后端。
    *   **P2/P3 (Stream):** 升级为实时分析和上下文维护（State Store），不仅分析情感，还分析意图、关键词等。
    *   **P4 (Recommender):** 基于实时分析结果和历史画像，调用 AI 生成回复建议。
    *   **P5 (WebSocket Handler):** 将 AI 生成的建议通过 WebSocket 实时推送给客户端界面。
*   **代码建议:** 引入 `spring-boot-starter-websocket`，实现 `WebSocketHandler` 和 `@MessageMapping`。设计低延迟的流处理拓扑。

---

#### **阶段四: 性能优化与 AI 集成深化**

*   **目标:** 针对实时交互场景，优化系统性能，提升 AI 分析和回复的效率与质量。
*   **当前状态:** 待完成阶段三后进入。
*   **主要任务:**
    *   **性能:** 优化 Kafka Streams 处理器（异步调用 AI API, 状态存储优化），优化 Kafka Producer/Consumer 配置。
    *   **AI 集成:** 精调 AI 提示词 (Prompt)，引入更复杂的 AI 模型或链式调用，提升分析和建议的准确性。
    *   **扩展功能:** 实现更高级的分析，如用户行为模式识别、长期关系健康度评估等。

---

### **可拓展功能建议**

1.  **多数据源支持:**
    *   **微信/IM 聊天记录:** 设计通用的数据导入模块，支持不同 IM 平台的聊天记录格式。
    *   **音视频评论分析:** 集成 STT (Speech-to-Text) 技术，分析视频/音频下方的文本评论。
    *   **社交媒体文本分析:** 抓取并分析微博、Twitter 等平台的文本。

2.  **分析维度扩展:**
    *   **意图识别:** 不仅分析情感，还识别用户说话的意图（询问、抱怨、邀请等）。
    *   **话题追踪:** 识别聊天中的主要话题，并分析话题下情感的变化。
    *   **语言风格分析:** 分析对方的语言习惯（正式/非正式、幽默/严肃）。

3.  **AI 功能增强:**
    *   **情境感知回复:** 结合历史聊天上下文、当前话题、对方情感和语言风格，生成更贴切的回复建议。
    *   **交往建议:** 基于长期的聊天数据分析，给出改善关系或沟通策略的建议。
    *   **风险预警:** 识别可能的负面沟通模式（如频繁争吵、冷暴力），发出预警。

4.  **可视化与交互:**
    *   **实时仪表盘:** 使用如 ECharts、D3.js 等库，开发更丰富的前端界面，实时展示情感趋势、预警信息、用户画像等。
    *   **报告生成:** 定期生成 PDF 报告，总结用户的社交情况、情绪变化、重要联系人分析等。

5.  **系统工程化:**
    *   **监控与告警:** 集成 Prometheus + Grafana 监控 Kafka、Kafka Streams 应用的性能指标。
    *   **容器化部署:** 使用 Docker 和 Docker Compose 编排部署整个应用栈（Kafka, Zookeeper, MySQL, Spring Boot 应用）。
    *   **API 网关:** 为前端和外部服务提供统一的 API 入口。