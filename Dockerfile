# ── Stage 1: Build ──────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /build

# 复制 Maven Wrapper 和 pom.xml（单独层，依赖变化才重新下载）
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY lib lib

# 安装快手 SDK 到本地 Maven 仓库
RUN ./mvnw install:install-file \
      -Dfile=lib/kuaishou-merchant-open-sdk-release_open_kwaishop_sdk-1.0.7633.jar \
      -DgroupId=com.kuaishou \
      -DartifactId=merchant-open-sdk \
      -Dversion=1.0.7633 \
      -Dpackaging=jar -q

# 预下载依赖（利用 Docker 层缓存，源码变化时不重新下载）
RUN ./mvnw dependency:go-offline -q

# 复制源码并打包
COPY src src
RUN ./mvnw package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 创建非 root 用户
RUN addgroup -S kwai && adduser -S kwai -G kwai
USER kwai

COPY --from=build /build/target/kwai_data-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
