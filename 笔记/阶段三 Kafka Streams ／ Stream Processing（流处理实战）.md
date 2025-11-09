# 阶段三 Kafka Streams ／ Stream Processing（流处理实战）

# Streams API 基础

### 🚀 详细知识点

Kafka Streams 是一个客户端库，用于构建**关键任务型**流式应用程序和微服务，其中输入和输出数据存储在 Kafka 集群中。它结合了流处理的简单性和 Kafka 的原生优势。

#### 1. KStream, KTable, GlobalKTable 的区别与应用场景

|**特性**|**KStream**|**KTable**|**GlobalKTable**|||||
| --| ----------------------------------------------------------------| ------------------------------------------------------------------------| ---------------------------------------------------------------| --| --| --| --|
|**数据含义**|​**事实的流（Log Stream/Changelog）** ​。每个数据记录都是一个**独立的、不可变**的事件（例如：销售订单、用户点击）。|​**键控表的抽象（Table View/State）** ​。每个键最新的记录代表该键的​**当前状态**（例如：用户账户余额、产品库存）。|​**键控表的抽象，但数据全局可用**。所有分区数据都复制到每个 Streams 实例。|||||
|**更新行为**|记录是**附加**的。没有“删除”或“更新”的概念，只有新的事件。|具有相同键的新记录会**覆盖**旧记录。特殊标记（tombstone）可用于逻辑删除。|与 KTable 相同，新记录覆盖旧记录。|||||
|**应用场景**|**事件驱动**处理、实时监控、日志处理、管道 ETL。|​**实时查询**​、聚合（如计算总和、平均值）、​**连接**（Join）维度数据。|连接**小型、变化缓慢**的维度数据（如邮编-城市映射、产品目录）。适用于广播数据。|||||
|**状态存储**|**无状态**转换不需要状态存储。**有状态**转换（如 Join/Windowing）需要。|​**默认有状态**，底层由 RocksDB 维护。|​**默认有状态**，数据复制到本地 RocksDB 实例。|||||
|**分区限制**|严格按照 Kafka 分区进行处理。|严格按照 Kafka 分区进行处理。​**Join 双方必须按键分区对齐**。|​**无分区限制**。Join 时，KStream/KTable 无需与 GlobalKTable 对齐。|||||

#### 2. 无状态转换 (Stateless Transformations)

- ​**定义**​：处理每条记录时，**不依赖**于先前处理过的任何记录的状态。
- ​**示例**：

  - ​`filter()`：根据条件过滤记录。
  - ​`map()`​ / `mapValues()`：转换记录的键和/或值。
  - ​`selectKey()`：重新分配记录的键。
  - ​`branch()`：根据条件将流分发到多个子流。

#### 3. 有状态转换 (Stateful Transformations)

- ​**定义**​：处理当前记录时，**需要利用**先前处理的记录所累积的状态。这些状态通常存储在​**本地状态存储**​（默认为 RocksDB）中，并由 Kafka Topic（Changelog Topic）进行​**容错和备份**。
- ​**示例**：

  - ​**Aggregation (聚合)** ​：`groupByKey()`​ 后跟 `count()`​, `reduce()`​, `aggregate()`​ 等。用于计算总和、平均值、计数等，通常带有​**时间窗口（Windowing）** 。
  - ​**Joins (连接)** ：

    - ​**Stream-Stream Join**​：连接两个 KStream。需要定义​**连接窗口**（Windowed Join）。
    - ​**Stream-Table Join**​：连接 KStream 和 KTable/GlobalKTable。这是一个**查找**操作，KStream 的事件触发 KTable 的当前状态查找。
  - ​**Windowing (窗口)** ：用于定义聚合操作的时间范围。常见的窗口类型有：

    - ​**Tumbling Window (滚动窗口)** ：固定大小，不重叠。
    - ​**Hopping Window (跳跃窗口)** ：固定大小，但可以重叠。
    - ​**Session Window (会话窗口)** ：根据活动间隙定义，用于跟踪用户会话。

---

### 🛠️ 如何使用

1. ​**添加依赖**​：在项目中引入 `kafka-streams` 依赖。
2. ​**配置 Kafka Streams**​：设置必要的配置，如 `application.id`​ (必须唯一，用于状态存储和消费者组)、`bootstrap.servers`​、`default.key.serde`​ 和 `default.value.serde`。
3. ​**创建拓扑 (Topology)** ​：使用 `StreamsBuilder` 对象构建处理逻辑。
4. ​**启动应用**​：创建 `KafkaStreams`​ 实例并调用 `start()` 方法。

**核心流程：**

1. ​`StreamsBuilder builder = new StreamsBuilder();`
2. ​**Source**​：`KStream<Key, Value> inputStream = builder.stream("input-topic");`
3. ​**Process**：

    - ​**无状态**​：`KStream<Key, NewValue> mappedStream = inputStream.mapValues(...);`
    - ​**有状态（聚合）** ：

      - ​`KTable<Windowed<Key>, Long> countTable = inputStream.groupByKey().windowedBy(TimeWindows.of(Duration.ofMinutes(5))).count();`
    - ​**有状态（连接）** ：

      - ​`KTable<JoinKey, TableValue> table = builder.table("dimension-topic");`
      - ​`KStream<Key, JoinedValue> joinedStream = inputStream.join(table, ...);`
4. ​**Sink**​：`mappedStream.to("output-topic");`
5. ​**Build & Run**​：`KafkaStreams streams = new KafkaStreams(builder.build(), props); streams.start();`

---

# 窗口操作

### 🚀 详细知识点

窗口（Windowing）是流处理中处理​**有界数据**（Bound Data）的关键概念。由于流数据是无限的，窗口允许我们将流切割成有限的、基于时间的片段，从而进行聚合（Aggregation）或连接（Join）等有状态操作。

#### 1. 窗口操作的基础：`groupByKey()`​ 和 `windowedBy()`

在 Kafka Streams 中，任何需要窗口的聚合操作都需要遵循以下模式：

1. ​**分组 (Grouping)** ​：使用 `KStream#groupByKey()`​ 或 `KStream#groupBy()`​ 将流转换为 `KGroupedStream`。
2. ​**开窗 (Windowing)** ​：对 `KGroupedStream`​ 调用 `windowedBy()` 并指定窗口类型。
3. ​**聚合 (Aggregation)** ​：在窗口上调用 `count()`​, `reduce()`​, 或 `aggregate()` 等方法。

#### 2. Tumbling Window (滚动窗口/固定时间窗口)

- ​**概念**：

  - 将流数据分割成​**固定大小**​、​**不重叠**​、**连续**的时间段。
  - 一个事件只属于一个窗口。
  - 常用于定期生成报告、计数或聚合固定时间段的数据。
- ​**特性**​：`size = advance`（窗口大小等于窗口向前移动的步长）。
- ​**时间戳**：窗口的开始时间是窗口的边界。
- ​**API**​：`TimeWindows.of(Duration.ofMinutes(N))`

#### 3. Hopping Window (跳跃窗口/滑动时间窗口)

- ​**概念**：

  - 将流数据分割成​**固定大小**​、**可能重叠**的时间段。
  - 通过定义一个小于窗口大小的  **“推进”步长（advance/hop）** ，实现窗口的滑动。
  - 一个事件可能属于多个窗口。
  - 常用于计算滑动平均值或需要平滑过渡的实时指标。
- ​**特性**​：`size >= advance`​（窗口大小大于或等于窗口步长）。如果 `size = advance`，则退化为 Tumbling Window。
- ​**时间戳**：窗口的开始时间是步长的倍数。
- ​**API**​：`TimeWindows.of(Duration.ofMinutes(N)).advanceBy(Duration.ofSeconds(M))`

#### 4. Session Window (会话窗口)

- ​**概念**：

  - 窗口的大小是**动态**的，由\*\*事件之间的不活动间隔（inactivity gap）\*\*决定。
  - 当一个键在给定的不活动间隔内没有新事件到达时，当前会话窗口关闭。
  - 常用于跟踪用户行为、网站点击流等\*\*突发性（bursty）\*\*活动。
- ​**特性**​：窗口之间​**不重叠**，大小不固定。
- ​**时间戳**：窗口的开始时间和结束时间由第一个事件和最后一个事件的时间戳决定。
- ​**API**​：`SessionWindows.with(Duration.ofSeconds(N))` (N 即为不活动间隔)。

#### 5. 迟到数据 (Late-arriving data)

- 窗口操作需要处理​**迟到数据**，即事件到达时，其对应的时间窗口可能已经关闭。
- Kafka Streams 允许设置​**容忍度 (Grace Period)** ，即窗口关闭后仍保持开放一段时间来接收迟到数据。
- ​**API**​：`TimeWindows.of(...).grace(Duration.ofMinutes(N))`。

---

### 🛠️ 如何使用

窗口操作是针对​**分组流**​（`KGroupedStream`）进行的有状态操作。

|**窗口类型**|**核心 API 结构**|||
| --| ------| --| --|
|**Tumbling Window**|​`groupedStream.windowedBy(TimeWindows.of(Duration.ofMinutes(5)))`|||
|**Hopping Window**|​`groupedStream.windowedBy(TimeWindows.of(Duration.ofMinutes(5)).advanceBy(Duration.ofMinutes(1)))`|||
|**Session Window**|​`groupedStream.windowedBy(SessionWindows.with(Duration.ofSeconds(30)))`|||

**聚合方法示例：**

在调用 `windowedBy()` 之后，通常紧接着调用以下聚合方法之一：

- ​**​`count()`​** ​：计算窗口内每个键的记录数，返回一个 `KTable<Windowed<Key>, Long>`。
- ​**​`reduce()`​** ​：通过一个 `Reducer` 函数将窗口内的所有值合并为一个新值。
- ​**​`aggregate()`​** ​：提供更大的灵活性，允许使用不同的值类型进行聚合（例如，输入是 `Long`​，输出是自定义的 `CustomObject`）。

---

# 状态存储

### 🚀 详细知识点

Kafka Streams 应用通常是**有状态**的，这意味着它们需要维护和更新状态（例如，聚合结果、Join 缓冲区的中间数据）。这些状态管理是 Streams API 容错和扩展能力的核心。

#### 1. 状态存储 (State Store)

- ​**定义**：用于存储有状态转换（如聚合、Join）结果的持久化、版本化的键值存储。
- ​**默认实现**​：​**RocksDB**。这是一个高性能的嵌入式键值存储库，由 Facebook 基于 LevelDB 优化而来。

  - ​**优点**：高性能、支持磁盘存储（避免内存溢出）、可配置性高。
- ​**存储位置**：状态数据存储在运行 Streams 应用的机器的本地文件系统上。每个 Stream Task 都会有一个或多个 RocksDB 实例。

#### 2. RocksDB 存储和容错机制

Kafka Streams 实现了​**两层容错机制**：

##### A. 本地持久化 (Local Persistence)

- ​**机制**​：RocksDB 将状态数据写入本地磁盘。这提供了**快速访问**和**重启恢复**能力。
- ​**作用**：即使 Streams 实例崩溃，重启后也可以从本地磁盘上的 RocksDB 恢复状态，避免从头开始处理整个 Kafka Topic。

##### B. 变更日志（Changelog）Topic 备份

- ​**机制**​：Streams API 会自动为每个状态存储创建一个内部 Kafka Topic，称为​**变更日志（Changelog）Topic**。

  - 每当本地状态存储中的数据发生变化（例如，聚合值更新），Streams 都会将这条更新记录（​**增量变更**）发送到对应的 Changelog Topic。
  - 这些 Topic 默认是 **紧凑型（Log Compaction）** 的，即 Kafka 会保留每个键的最新值，用于高效的恢复。
- ​**作用**​：提供了**远程容错**和**弹性伸缩**能力。

  - ​**容错**​：如果 Streams 实例或其本地磁盘彻底损坏，一个新的实例启动时可以从 Changelog Topic 消费所有历史变更记录，**重建**本地的 RocksDB 状态存储。
  - ​**负载均衡/伸缩**：当一个新的实例加入或现有实例离开时，Kafka 分区和状态存储的所有权会重新分配。新的 Task 可以从 Changelog Topic 恢复其负责分区对应的状态。

#### 3. 交互式查询 (Interactive Query)

- ​**概念**：

  - 允许用户直接查询 Kafka Streams 应用内部维护的本地状态存储（RocksDB）。
  - Streams 应用将自己暴露为一个**嵌入式数据库**或​**微服务**。
- ​**用途**：

  - ​**实时仪表板**：直接查询最新聚合结果，而不是通过另一个 Topic 接收结果并存储到外部数据库。
  - ​**微服务**：构建一个可以提供实时、低延迟查询服务的应用（例如，查询实时库存、用户会话状态等）。
  - ​**路由查询**​：由于状态存储是​**分区的**（每个 Streams 实例只存储部分数据），客户端需要知道哪个 Streams 实例持有它想查询的键。Streams API 提供了元数据服务来帮助路由查询请求。
- ​**实现方式**：

  1. 在 Streams 应用内部开启一个嵌入式 Web Server (如 Jetty)。
  2. 利用 `KafkaStreams#store(StoreQueryParameters)` 获取状态存储的句柄。
  3. 通过 HTTP/RESTful 接口接收查询请求。

‍

---

# why 流式处理？

## 流式处理的作用和优势

### 1. 流式处理的核心用途

流式处理主要用于**实时数据处理和分析**，它能够：

- **实时聚合统计**：如计算点击量、访问次数等指标
- **复杂事件处理**：检测特定模式或异常行为
- **数据转换和丰富**：将原始数据转换为更有意义的格式
- **实时告警**：基于业务规则触发即时通知

### 2. 与直接读取消息的区别

|特性|直接读取Kafka消息|Kafka Streams流式处理|
| ------| ------------------------------| ----------------------------|
|**处理复杂度**|简单读取，需手动实现业务逻辑|提供丰富的流处理操作符|
|**状态管理**|需要自己维护状态|内置状态存储和管理|
|**窗口操作**|需手动实现时间窗口逻辑|内置滚动窗口、滑动窗口支持|
|**容错性**|需要自行实现容错机制|自动处理故障恢复|
|**扩展性**|需要手动分片和负载均衡|自动分布式处理|

### 3. 实际开发中的应用场景

```java
// 在ClickStreamProcessor中展示的典型用例：
private void processTumblingWindow(KStream<String, String> sourceStream) {
    sourceStream
        .groupByKey()
        // 时间窗口聚合 - 每10秒统计一次点击量
        .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10)))
        .count(Materialized.as(CLICK_COUNT_STORE))
        .toStream()
        .map((windowedKey, value) -> new KeyValue<>(windowedKey.key(),
            "Window [" + windowedKey.window().startTime() + " - " +
            windowedKey.window().endTime() + "]: Count = " + value))
        .to(CLICK_COUNT_OUTPUT, Produced.with(Serdes.String(), Serdes.String()));
}
```

### 4. 为什么选择流式处理

1. **实时性**：不需要等待批处理作业，数据到达即处理
2. **简化开发**：提供高级API，减少手动实现复杂逻辑的工作量
3. **可靠性**：自动处理容错、恢复和状态管理
4. **性能优化**：内置优化机制，如记录缓存、批处理等
5. **可扩展性**：支持水平扩展，自动负载均衡

在实际业务中，如果只需要简单的消息转发或存储，直接读取Kafka就足够了。但当需要进行复杂的数据分析、实时计算或事件关联时，流式处理框架如Kafka Streams就显得非常必要。

# 如何选择？

## 🆚 流处理 vs. 消息发送：应用场景对比

|**决策因素**|**简单的消息发送/传输**|**流处理 (Kafka Streams/Flink)**||||
| --| ----------------------------------------------| -------------------------------------------------------| --| --| --|
|**核心目的**|数据的​**异步传输、解耦**。|数据的​**实时计算、状态维护、业务逻辑**。||||
|**数据特性**|​**原子事件**，只关注事件本身。|​**数据流**，关注事件间的关系、时序和状态。||||
|**处理模式**|​**点对点/发布-订阅**：接收即处理。|​**持续计算**：基于历史和窗口的持续聚合。||||
|**状态需求**|​**无状态**：处理单个消息，不依赖之前或之后的消息。|​**有状态**：需要维护中间状态（如计数、总和、Join 缓冲区）。||||
|**数据源**|​**单一输入**：只关注从一个 Topic/Queue接收的消息。|​**多源输入**：需要关联（Join）多个 Topic 的数据。||||

---

## 🛠️ 什么时候选择简单的消息发送/传输？

当你的需求仅限于**解耦、异步通信和简单的一次性处理**时，使用 Kafka Producer 和 Consumer 就足够了。

### 1. 异步通信与解耦

- ​**场景**：用户下单后，需要通知库存服务、邮件服务和积分服务。
- ​**实现**​：订单服务发布一条 `OrderPlaced`​ 消息到 Kafka。Consumer 分别由库存、邮件、积分服务实现，它们只负责读取消息并执行自己的**单一、无状态**任务（如：邮件服务只负责发送邮件）。
- ​**特点**：每个服务独立工作，不影响彼此。

### 2. ETL 管道（简单传输）

- ​**场景**：从数据库捕获变更数据 (CDC) 到 Kafka，然后将数据原封不动地加载到数据湖/数仓。
- ​**实现**​：Producer 将原始数据发送到 `raw-data` Topic。Consumer 将数据直接写入 S3/HDFS。
- ​**特点**：数据内容没有实时计算或复杂的转换。

### 3. 实时通知与日志记录

- ​**场景**：网站点击日志、服务器错误告警、APP 实时推送通知。
- ​**实现**：客户端/服务器发送日志或通知事件。Consumer 接收后，直接写入 ELK 或发送给通知 API。
- ​**特点**​：消息处理是​**即时且独立的**。

---

## 💻 什么时候选择流处理（Kafka Streams/Flink）？

当你需要**在数据流上执行复杂的、有状态的、时间敏感的计算**时，你就需要流处理技术。

### 1. 实时聚合与统计 (Aggregation)

- ​**场景**：实时计算每 5 分钟内，每个城市的订单总额；计算用户 10 分钟内的点击次数；计算滑动平均值。
- ​**实现**​：使用 **Kafka Streams** 的 `groupByKey()`​ + `windowedBy()`​ + `aggregate()`​ 或 `count()`。
- ​**特点**​：​**需要状态存储**（RocksDB）来累加和维护中间结果。

### 2. 数据关联与丰富 (Join & Enrichment)

- ​**场景**：实时交易事件需要立即关联用户的最新信用评级或产品的名称。
- ​**实现**​：使用 ​**Stream-Table Join**​，将交易流 (`KStream`​) 与用户档案/产品目录 (`KTable`​/`GlobalKTable`) 进行连接。
- ​**特点**​：需要**多源输入**和**状态存储**来保存维度数据。

### 3. 模式识别与会话分析 (Sessionization & Pattern Detection)

- ​**场景**：识别用户在 30 秒内“浏览商品”后立即“添加到购物车”的事件序列；跟踪一个完整的用户会话。
- ​**实现**​：使用 **Session Window** 或复杂的**拓扑操作**来检测序列。
- ​**特点**​：处理逻辑依赖于**事件的时序**和​**状态的维护**。

### 4. 复杂业务逻辑与事件驱动微服务

- ​**场景**：实时反欺诈系统（基于历史行为和多维度的实时判断）、库存的实时扣减和回滚逻辑。
- ​**实现**​：将复杂的业务规则编码进 Streams 拓扑中，利用其**事务性**和\*\*精确一次（Exactly-Once）\*\*语义。
- ​**特点**​：处理逻辑非常复杂，需要**高可靠性**和​**实时性**。

---

## 总结：从“简单传输”到“数据计算”

简单来说：

1. ​**如果你只是想把数据从 A 搬到 B，或者做一次简单的、无状态的转换**​：使用 ​**Kafka Producer/Consumer**。
2. ​**如果你需要基于时间、基于历史、基于多个数据源，实时计算出一个新的结果或状态**：使用 **Kafka Streams** (或 Flink 等专门的流处理引擎)。

‍

‍
