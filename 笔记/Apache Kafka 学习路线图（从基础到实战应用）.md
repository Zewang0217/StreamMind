# Apache Kafka 学习路线图（从基础到实战应用）

### 阶段一：Kafka 核心基础与架构（理论与简单实践）

**目标：**  理解 Kafka 的设计哲学、核心组件及其工作原理。

|**模块**|**核心内容**|**实践代码（Java/CLI）**||||
| --| -------------------------------------------------------------------| -------------------------------------------------------------------------------------------------| --| --| --|
|**1. 架构与设计**|**Broker, Producer, Consumer, Topic, Partition**是什么？Kafka 的持久化机制（Log Segments）。为什么 Kafka 速度快？|使用 Kafka CLI (命令行工具) 或 Docker​**手动创建 Topic**，并查看 Topic 的详细信息（分区数、副本数）。||||
|**2. 生产者 (Producer)**|​**同步发送 vs. 异步发送**​。`Acks`​配置（0, 1, All）对性能和持久性的影响。**分区器 (Partitioner)** 的作用和定制。|​**Spring Boot Producer**​：编写一个简单的 REST 接口，使用`KafkaTemplate`​异步发送消息，并配置不同的`Acks`等级进行测试。||||
|**3. 消费者 (Consumer)**|**Consumer Group**和​**Partition 消费关系**​。​**Offset**​（偏移量）的意义、提交机制（自动/手动提交）。**消息重复消费 (At Least Once)** 的处理。|​**Spring Boot Consumer**：实现手动提交 Offset，并模拟业务处理失败（抛出异常），观察 Kafka 如何重试和处理 Offset。||||
|**4. 序列化与数据格式**|**Serializer**和**Deserializer**的原理。为什么要用**Avro/Protobuf**等模式定义？|编写自定义的**POJO 序列化器**(JSON/Jackson) 和反序列化器，用于在 Producer 和 Consumer 之间传递自定义 Java 对象。||||

### 阶段二：Kafka 高级特性与运维（深入理解）

**目标：**  掌握 Kafka 的高可用性、性能优化和日常运维技巧。

|**模块**|**核心内容**|**实践代码（Java/CLI）**||||
| --| ----------------------------------------------------------------------------------------------------| ---------------------------------------------------------------------------------------------------------| --| --| --|
|**5. 高可用性与副本**|**ISR (In-Sync Replicas)** 机制。**Leader 选举**过程。最小同步副本数 (`min.insync.replicas`) 的作用。|在三节点 Docker 集群中，​**手动停止 Leader Broker**，观察 Kafka 如何自动进行 Leader 选举，并观察 Producer 和 Consumer 的行为。||||
|**6. Kafa Connect**|概念与用途。Source Connector（数据源到 Kafka）和 Sink Connector（Kafka 到数据目的地）。|部署​**Kafka Connect 容器**​，使用预制的**JDBC Source Connector**将 MySQL 数据库中的表数据同步到 Kafka 的一个 Topic 中。||||
|**7. 性能优化**|Producer 端的 Batching, Compression。Consumer 端的 Batching。Broker 端的 OS 调优（磁盘、页缓存）。|在 Producer 中调整`batch.size`​和`linger.ms`参数，使用 JMeter 或简单循环测试工具，比较吞吐量变化。||||

### 阶段三：Kafka Streams / Stream Processing（流处理实战）

**目标：**  掌握如何使用 Kafka Streams 或 KSQLDB（可选）在 Kafka 内部进行有状态的、实时的流式计算。

|**模块**|**核心内容**|**实践代码（Java）**||||
| --| ------------------------------------------------------------------| ------------------------------------------------------------------------------------------------------------| --| --| --|
|**8. Streams API 基础**|**KStream, KTable, GlobalKTable**的区别与应用场景。​**无状态转换**​（Filter, Map）与​**有状态转换**（Aggregate, Join）。|​**流处理 App (Topology)** ​：实现一个简单的 Kafka Streams 应用，用于**实时统计**某个 Topic 中每 10 秒内收到的消息数量（Count）。||||
|**9. 窗口操作 (Windowing)**|​**Tumbling Window**​（固定时间窗口）、​**Hopping Window**​（滑动时间窗口）和**Session Window**的概念及代码实现。|**实时指标计算：** 实现一个 Hopping Window，用于实时计算用户在过去 5 分钟内的平均点击率（假设数据流中有用户 ID 和点击事件）。||||
|**10. 状态存储 (State Store)**|**RocksDB**存储和**容错**机制。**交互式查询**(Interactive Query) 的概念。|尝试查询 Kafka Streams 应用的状态存储，以获取当前窗口的实时统计结果，无需通过新的 Topic。||||

### 阶段四：综合项目：Java AI 实时聊天分析系统

**目标：**  整合 Spring Boot, Kafka, Kafka Streams, 和 Java AI SDK（Gemini API）来构建一个具备实时分析能力的综合应用。

|**步骤**|**任务描述**|**涉及技术**||||
| --| ----------------------------------------------------------------------------------------------------------------------------------------------------| ---------------------------------------------| --| --| --|
|**P1. 数据采集与生产**|编写一个​**Spring Boot Producer**​，模拟一个聊天室应用，持续向 Kafka Topic (`chat-messages`​) 发送包含`(userId, timestamp, message)`的 JSON 消息。|Spring Boot,`KafkaTemplate`||||
|**P2. 实时情感分析 (AI 集成)**|部署一个​**Kafka Streams Processor**​。该处理器订阅`chat-messages`Topic，对每一条消息：<br /><br />1.**调用 Gemini API**对`message`内容进行情感分析（Positive, Neutral, Negative）。<br /><br />2. 将结果写入一个新的 Topic (`sentiment-scores`)。|Kafka Streams,**Gemini Java SDK**(或直接 REST API), JSON Serde||||
|**P3. 实时预警计算**|部署第二个​**Kafka Streams Processor**​。该处理器订阅`sentiment-scores`​Topic，使用​**时间窗口**​（例如，每 30 秒）计算过去 1 分钟内所有消息的​**平均情感得分**。|Kafka Streams, Hopping Window, Aggregation||||
|**P4. 结果展示与持久化**|编写一个​**Spring Boot Consumer**​，订阅`sentiment-scores`​Topic 的结果。当平均情感得分低于某个阈值（例如：-0.5）时，触发警报逻辑（例如：写入数据库的`alert_table`或发送通知）。|Spring Boot,`KafkaListener`, MySQL/JPA||||

‍
