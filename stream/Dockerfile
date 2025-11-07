# stream/Dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 复制实际构建的jar文件
COPY target/stream-*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
