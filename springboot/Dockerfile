# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# 先只拷 pom，利用 Docker 层缓存，改代码不必重下依赖
COPY pom.xml .
# 提前下依赖 —— 关键优化
RUN mvn -B -q dependency:go-offline
COPY src ./src
# 编译打包
RUN mvn -B -q clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# 只取出jar包
COPY --from=build /build/target/*.jar app.jar
EXPOSE 9090
# 2G 服务器必须限制堆内存，避免 OOM
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "/app/app.jar"]