# 后端 Docker 镜像：Spring Boot 4 + JDK 21
FROM eclipse-temurin:21-jre-alpine

# 安装 curl 用于健康检查（alpine 精简镜像默认没有 wget/curl）
RUN apk add --no-cache curl

WORKDIR /app

# 构建产物由宿主机 mvn package 生成后 COPY 进来
# （不在容器内编译，避免镜像体积过大和构建慢）
COPY target/backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# JVM 参数：容器内存友好
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
