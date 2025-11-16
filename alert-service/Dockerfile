# 使用OpenJDK 17作为基础镜像
FROM openjdk:17-jdk-slim

# 安装Maven
RUN apt-get update && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /app

# 复制pom.xml和源代码
COPY pom.xml .
COPY src ./src

# 构建项目
RUN mvn clean package -DskipTests

# 复制构建好的jar包
RUN cp target/*.jar app.jar

# 暴露端口
EXPOSE 8084

# 运行应用
CMD ["java", "-jar", "app.jar"]