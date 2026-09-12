# 手相研究所（AI-PALM-READING）

AI + 赛博玄学娱乐解读：单人手相识别、SSE 流式分析报告、深度解锁、稀有印记私域、CP 合拍、周记录能量档案。

## 仓库结构

```
stitch_/
├── backend/          # Spring Boot 3.2 后端（按特性分包，Maven 集成前端构建）
│   └── src/
│       └── com.palmistrylab.api
│           ├── web/        # 控制器（System/Palm/Record/Cp/Metrics）
│           ├── palm/       # 分析编排/几何解析/图像校验/合规/叙事生成/会话
│           ├── record/     # 周记录/日历/月报（窄投影 + 事务）
│           ├── cp/         # CP 合拍
│           ├── llm/        # OpenAI 兼容 LLM 客户端（重试分类/流式）
│           ├── perception/ # 感知边车客户端
│           ├── metrics/    # 埋点与运营指标
│           ├── identity/   # 签名用户身份
│           ├── media/      # 照片落盘存储
│           └── common/     # 限流/令牌门禁/异常映射/线程池
├── frontend/         # Vite 多页应用（唯一源码，构建产物输出到 backend 静态资源）
├── perception/       # FastAPI + MediaPipe 感知边车（可选）
└── docs/             # PRD / 技术设计文档
```

## 本地开发

```bash
# 后端（dev profile 默认 MySQL；run-dev.sh 使用 H2 文件库）
cd stitch_/backend && mvn spring-boot:run

# 前端热更新开发（/api 代理到 8080）
cd stitch_/frontend && npm install && npm run dev

# 前端与后端一起构建（frontend-maven-plugin 自动构建 Vite）
mvn verify
# 纯后端迭代跳过前端构建：
mvn test -Dskip.installnodetools -Dskip.npm
```

前端详见 `frontend/README.md`；环境变量样例见 `.env.example`。

## Docker 部署

```bash
cd stitch_
cp .env.example .env      # 填入 LLM_API_KEY、USER_TOKEN_SECRET、ADMIN_TOKEN
docker compose up -d      # mysql + backend（--profile perception 追加感知边车）
```

## API 一览（前缀 /api/v1）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /health | 健康检查 |
| GET | /user/identity | 签发/续签签名 userId（?previous= 续用旧身份） |
| POST | /palm/analyze | 单人分析（图片校验并入同一次视觉模型调用） |
| POST | /palm/analyze/stream | 同上，SSE 流式：stage → progress → done/error |
| POST | /palm/unlock-deep | 深度报告解锁 |
| POST | /palm/rare-mark | 稀有印记查询（私域引导） |
| POST | /palm/validate-image | 手掌图片校验（AI + 同图缓存） |
| POST | /cp/analyze | CP 合拍分析 |
| POST | /cp/unlock-deep | CP 深度解锁 |
| POST | /record/weekly | 周记录（照片落盘，DB 只存相对路径） |
| GET | /record/calendar | 月历（窄投影，不拉取图片大字段） |
| GET | /record/detail | 单日回顾 |
| POST | /record/note | 更新当日便签 |
| GET | /record/monthly-report | 月度能量报告（事务 + 悲观锁 upsert） |
| POST | /events/track | 埋点（异步落库） |
| GET | /metrics/summary | 运营指标（配置 ADMIN_TOKEN 后需 X-Admin-Token 头） |
| POST | /llm/ping | LLM 探活 |

限流：/api/** 固定窗口（默认 60s / 90 次 / IP+URI），429 返回 `RATE_LIMITED`。

## 环境变量

| 变量 | 说明 |
|---|---|
| MYSQL_URL / MYSQL_USERNAME / MYSQL_PASSWORD | 数据库连接 |
| LLM_ENABLED / LLM_BASE_URL / LLM_API_KEY / LLM_MODEL | SiliconFlow（OpenAI 兼容）|
| LLM_VISION_MODEL / LLM_FALLBACK_MODEL | 视觉模型与兜底模型 |
| LLM_CONNECT_TIMEOUT_SECONDS / LLM_READ_TIMEOUT_SECONDS | 默认 5s / 30s |
| USER_TOKEN_SECRET | 用户身份签名密钥（生产必改） |
| ADMIN_TOKEN | 运营看板访问令牌（留空不鉴权） |
| PERCEPTION_BASE_URL | 感知边车地址（留空走 imageHash 确定性兜底） |
| PHOTOS_DIR | 照片落盘目录（默认 ./data/photos） |
| CORS_ALLOWED_ORIGINS | CORS 白名单 |
| RATE_LIMIT_ENABLED / RATE_LIMIT_WINDOW_SECONDS / RATE_LIMIT_MAX_REQUESTS | 限流参数 |

## 测试与 CI

- `mvn verify`：49 个测试（服务单测、LLM 重试链/SSE 解析、轨迹几何、限流器、控制器装配、照片落盘）。
- GitHub Actions（.github/workflows/ci.yml）：push/PR 自动执行 `mvn verify`。
