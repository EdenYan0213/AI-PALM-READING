# 感知层边车（Perception Sidecar）

V0.2 感知层（TD §3 / §13.1 收窄版）：手掌检测 + ROI 裁剪 + 质量分 + 掌型几何。
**不做掌纹主线 CV 提取**（精度 <70%，由视觉 LLM 在生成层辅助标注质感特征）。

## 复用说明
- [MediaPipe Hands](https://github.com/google-ai-edge/mediapipe)（Google 官方，Apache 2.0）：21 点手部关键点，模型内置 pip 包，无需单独下载 ONNX
- 服务骨架参考 [Hesham942/learn2sign](https://github.com/Hesham942/learn2sign) 的 base64→JSON 模式
- 模糊度 = OpenCV 拉普拉斯方差（行业通用做法）

## API

`GET /health` → `{status, service, version}`

`POST /detect`
```json
{ "imageData": "data:image/jpeg;base64,..." }
```

响应（检测到手）：
```json
{
  "detected": true,
  "handedness": "Left",
  "landmarks": [[x,y,z] x 21],
  "palmGeometry": {
    "palmRatio": 1.24,
    "fingerRatio": 0.83,
    "suggestedPalmShape": "水型手"
  },
  "quality": { "blur": 123.4, "brightness": 142.0, "retake": false, "hints": [] },
  "roiJpeg": "<base64>",
  "processingTimeMs": 45.2
}
```

掌型判定规则（与 Java 侧 palmistry-rules 对齐）：
- 掌长/掌宽 < 1.10 → 方掌，否则长掌
- 指长/掌长 ≥ 0.80 → 长指，否则短指
- 方掌+短指=土 ｜ 方掌+长指=风 ｜ 长掌+短指=火 ｜ 长掌+长指=水

## 本地运行
```bash
python3.11 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app:app --port 8100
```

## Docker
```bash
docker build -t palmistry-perception .
docker run -p 8100:8100 palmistry-perception
```

## Java 侧接入
后端 `PerceptionClient` 通过 `PERCEPTION_BASE_URL`（默认空 = 关闭）调用本服务；
超时 3s、失败静默降级为主流程的确定性兜底（imageHash 规则），不影响可用性。
