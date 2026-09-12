# 手相研究所后端镜像（含前端构建产物）
# 构建上下文为 stitch_/ 目录：docker build -f backend/Dockerfile .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先拷 pom 依赖层（利用 Docker 缓存加速）
COPY backend/pom.xml backend/pom.xml
RUN cd backend && mvn -B -q dependency:go-offline -Dskip.installnodetools -Dskip.npm || true

# 再拷源码与前端工程（frontend-maven-plugin 会在 generate-resources 阶段自动构建 Vite）
COPY frontend/ frontend/
COPY backend/src backend/src
RUN cd backend && mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre
RUN useradd --system --home /app --shell /usr/sbin/nologin palmistry
WORKDIR /app
COPY --from=build /app/backend/target/palmistry-backend-*.jar app.jar
RUN mkdir -p /app/data/photos && chown -R palmistry:palmistry /app
USER palmistry
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
