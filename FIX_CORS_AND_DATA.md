# 🔧 修复CORS和数据获取问题

## 📋 问题分析

### 1. CORS错误
**错误**：`When allowCredentials is true, allowedOrigins cannot contain the special value "*"`

**原因**：当`allowCredentials(true)`时，不能使用通配符`*`，必须指定具体的源。

### 2. 数据获取问题
**现象**：仪表盘显示离线，无数据
**可能原因**：
- CORS配置错误导致前端无法调用API
- 数据库中无数据
- API端点不正确
- 前端JavaScript错误

## 🛠️ 修复方案

### 1. 修复CORS配置

**修改WebConfig.java**：
```java
@Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
            .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600);
}
```

### 2. 修复前端JavaScript

**修改fetch调用**：
```javascript
// 移除credentials，简化CORS处理
const response = await fetch(`${this.apiBaseUrl}/health`, {
    method: 'GET',
    headers: {
        'Content-Type': 'application/json'
    }
});
```

### 3. 数据获取逻辑检查

**检查数据流向**：
1. **Stream-Analyzer** → 处理Kafka数据 → 发送到Kafka主题
2. **Collector-Service** → 收集数据 → 发送到Kafka主题
3. **Alert-Service** → 消费处理后的数据 → 保存到数据库
4. **前端** → 调用Alert-Service API → 获取数据展示

## 🧪 调试步骤

### 1. 检查服务状态
```bash
# 检查端口
netstat -tuln | grep -E ':(8084|8085|8087)'

# 检查进程
ps aux | grep -E 'alert-service|collector|stream-analyzer'
```

### 2. 测试API端点
```bash
# 测试健康检查
curl http://localhost:8084/api/dashboard/health

# 测试统计API
curl http://localhost:8084/api/dashboard/stats

# 测试预警列表
curl http://localhost:8084/api/dashboard/alerts/recent?limit=5
```

### 3. 检查数据库
```bash
# 检查数据库连接（如果psql可用）
psql -h localhost -U admin -d streamdb -c "SELECT COUNT(*) FROM alert_messages;"
```

### 4. 查看日志
```bash
# 查看服务日志
tail -f alert-service/logs/application.log
```

## 📊 数据流向验证

### 1. 检查Kafka数据流
```bash
# 检查Kafka主题
kafka-topics.sh --list --bootstrap-server localhost:9092

# 检查消息流
kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic social-messages --from-beginning --max-messages 5
```

### 2. 检查数据库数据
```sql
-- 检查预警数据
SELECT COUNT(*) FROM alert_messages;
SELECT * FROM alert_messages ORDER BY created_at DESC LIMIT 5;
```

## 🎯 成功标准

当系统正常运行时：
1. ✅ 所有服务端口正常监听
2. ✅ API端点可以正常访问
3. ✅ 前端可以正常调用API
4. ✅ 数据库中有数据
5. ✅ 仪表盘显示正常数据
6. ✅ 无CORS错误

## 🚀 快速修复

1. **修复CORS配置**（已完成）
2. **修复前端JavaScript**（已完成）
3. **重启所有服务**
4. **验证功能**

**现在运行修复后的系统，应该可以正常工作了！** 🎉