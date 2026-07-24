# ── Build stage: Maven + JDK 21 ──
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

# 阿里云 Maven 镜像（国内加速）
COPY settings.xml /root/.m2/settings.xml

# 先拉依赖（Docker 层缓存）
COPY pom.xml .
RUN mvn dependency:resolve -DskipTests -q

# 源码 + 构建
COPY src ./src
RUN mvn package -DskipTests -q

# ── Runtime stage: JRE 21 slim ──
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 时区
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# Copy jar
COPY --from=build /build/target/*.jar app.jar

# 上传文件目录（挂载点）
RUN mkdir -p /app/file

EXPOSE 8077

ENTRYPOINT ["java", "-jar", "app.jar"]
