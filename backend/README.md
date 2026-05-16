# Palmistry Backend (Spring Boot 3.2)

## Environment
- Java: 17+
- Maven: 3.9+
- Spring Boot: 3.2.12

## Run
```bash
cd backend
mvn spring-boot:run
```

Default profile: `dev` (H2).

Run with explicit profile:
```bash
# dev
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# prod (MySQL)
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

For `prod`, configure env vars:
```bash
MYSQL_URL=jdbc:mysql://localhost:3306/palmistry?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai
MYSQL_USERNAME=root
MYSQL_PASSWORD=your_password
```

## LLM Integration (ModelScope OpenAI-compatible)
The backend supports OpenAI-compatible chat completion and can generate report text from real model output.

Env vars:
```bash
LLM_ENABLED=true
LLM_BASE_URL=https://api-inference.modelscope.cn/v1
LLM_API_KEY=your_modelscope_token
LLM_MODEL=Qwen/Qwen3.5-397B-A17B
LLM_FALLBACK_MODEL=ZhipuAI/GLM-5.1
```

When enabled, these endpoints will produce LLM-generated content (with safe fallback on failure):
- `POST /api/v1/palm/analyze`
- `POST /api/v1/palm/unlock-deep`
- `POST /api/v1/cp/analyze`

Service starts at `http://localhost:8080`.

H2 console: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:file:./data/palmistry-db`
- User: `sa`

## API
### Health
- `GET /api/v1/health`

### Single Palm Analyze
- `POST /api/v1/palm/analyze`
```json
{
  "source": "camera",
  "handSide": "left",
  "gender": "unknown"
}
```

`recordMode` is optional. For normal analyze use `single`/`standard`; for weekly journaling flows use `quick` or `full`.

### Unlock Single Deep Report
- `POST /api/v1/palm/unlock-deep`
```json
{
  "sessionId": "PALM-XXXXXXX"
}
```

### Rare Mark Query
- `POST /api/v1/palm/rare-mark`
```json
{
  "sessionId": "PALM-XXXXXXX"
}
```

### CP Analyze
- `POST /api/v1/cp/analyze`
```json
{
  "userA": {
    "nickname": "你",
    "handType": "水型手",
    "mbti": "INFJ"
  },
  "userB": {
    "nickname": "TA",
    "handType": "火型手",
    "mbti": "ENFP"
  }
}
```

### Unlock CP Deep Report
- `POST /api/v1/cp/unlock-deep`
```json
{
  "sessionId": "CP-XXXXXXX"
}
```

### Track Event
- `POST /api/v1/events/track`
```json
{
  "eventName": "share_card",
  "sessionId": "CP-XXXXXXX",
  "channel": "cp_page"
}
```

### Metrics Summary
- `GET /api/v1/metrics/summary`

### Weekly Record (Step-2)
- `POST /api/v1/record/weekly`
```json
{
  "userId": "guest-demo",
  "recordMode": "quick",
  "imageData": "data:image/jpeg;base64,...",
  "recordDate": "2026-04-27"
}
```

### Record Calendar (Step-2)
- `GET /api/v1/record/calendar?userId=guest-demo&yearMonth=2026-04`

### Record Detail (Step-2)
- `GET /api/v1/record/detail?userId=guest-demo&date=2026-04-27`

### Monthly Energy Report (Step-2)
- `GET /api/v1/record/monthly-report?userId=guest-demo&yearMonth=2026-04`

### Update Record Note (Step-2)
- `POST /api/v1/record/note`
```json
{
  "recordId": "12",
  "userNote": "今天状态不错"
}
```

## Frontend Integration
Current frontend pages use:
- `http://localhost:8080/api/v1/palm/analyze`
- `http://localhost:8080/api/v1/palm/unlock-deep`
- `http://localhost:8080/api/v1/palm/rare-mark`
- `http://localhost:8080/api/v1/cp/analyze`
- `http://localhost:8080/api/v1/cp/unlock-deep`

If you change backend port/domain, update `API_BASE` constant in:
- `ai..._light/code.html`
- `light_3/code.html`
- `cp_light/code.html`

Admin dashboard:
- `../admin_metrics.html` (workspace page)
- Reads: `GET /api/v1/metrics/summary`
