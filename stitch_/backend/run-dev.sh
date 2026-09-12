#!/bin/bash
# 本地启动后端（读取 git-ignored 的 .env.local）
set -a; source "$(dirname "$0")/.env.local"; set +a
cd "$(dirname "$0")"
H2_URL="jdbc:h2:file:./build/h2-dev;MODE=MySQL"
SPRING_DATASOURCE_URL="$H2_URL" SPRING_DATASOURCE_USERNAME=sa SPRING_DATASOURCE_PASSWORD="" \
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver \
mvn -q spring-boot:run
