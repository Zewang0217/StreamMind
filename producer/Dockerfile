# 可以暂时简化 Dockerfile，专注于基础设施搭建
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# 使用占位文件或空jar直到有实际代码
COPY placeholder.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
