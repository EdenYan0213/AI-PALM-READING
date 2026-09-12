"""手相研究所 · 感知层边车（V0.2, TD §3/§13）

职责（按 §13 收窄后的范围）:
  - 手掌检测 + 21 点关键点（复用官方 MediaPipe Hands, Apache 2.0）
  - ROI 裁剪（关键点外扩框, 返回 base64 JPEG）
  - 质量分（模糊度 = 拉普拉斯方差 / 亮度 / 建议重拍）
  - 掌型几何（掌长宽比 + 指长/掌长比 → 建议手型: 土/风/火/水）

明确不做（§13.1）: 掌纹主线不做传统 CV 提取（学术难题, 精度 <70%）。

复用来源:
  - MediaPipe Hands: https://github.com/google-ai-edge/mediapipe (Apache 2.0)
  - 服务模式参考: Hesham942/learn2sign 的 base64->JSON FastAPI 模式
启动: uvicorn app:app --host 0.0.0.0 --port 8100
"""

import base64
import math
import time

import cv2
import numpy as np
import mediapipe as mp
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="palmistry-perception", version="0.2.0")

_hands = mp.solutions.hands.Hands(
    static_image_mode=True,
    max_num_hands=1,
    min_detection_confidence=0.5,
)

# 掌型判定阈值（TD §4: 阈值参数化, 不写死业务逻辑里; 边车先内置, Java 侧规则文件对齐）
PALM_RATIO_SQUARE = 1.10   # 掌长/掌宽 < 1.10 视为方掌
FINGER_RATIO_LONG = 0.80   # 指长(中指尖-中指根)/掌长 > 0.80 视为长指
BLUR_PASS = 80.0           # 拉普拉斯方差下限
BRIGHTNESS_LOW = 60.0
BRIGHTNESS_HIGH = 200.0


class DetectRequest(BaseModel):
    imageData: str  # data URL 或裸 base64


def _decode_image(image_data: str):
    payload = image_data.split(",", 1)[1] if image_data.startswith("data:") else image_data
    buf = np.frombuffer(base64.b64decode(payload), dtype=np.uint8)
    return cv2.imdecode(buf, cv2.IMREAD_COLOR)


def _encode_jpeg_b64(frame) -> str:
    ok, encoded = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 88])
    return base64.b64encode(encoded).decode("ascii") if ok else ""


def _dist(a, b) -> float:
    return math.hypot(a.x - b.x, a.y - b.y)


def _suggest_shape(palm_ratio: float, finger_ratio: float) -> str:
    square_palm = palm_ratio < PALM_RATIO_SQUARE
    long_finger = finger_ratio >= FINGER_RATIO_LONG
    if square_palm and not long_finger:
        return "土型手"
    if square_palm and long_finger:
        return "风型手"
    if not square_palm and not long_finger:
        return "火型手"
    return "水型手"


@app.get("/health")
def health():
    return {"status": "ok", "service": "palmistry-perception", "version": "0.2.0"}


@app.post("/detect")
def detect(req: DetectRequest):
    started = time.time()
    frame = _decode_image(req.imageData)
    if frame is None:
        return {"detected": False, "reason": "image_decode_failed", "retake": True}

    height, width = frame.shape[:2]
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    blur_score = float(cv2.Laplacian(gray, cv2.CV_64F).var())
    brightness = float(gray.mean())

    result = _hands.process(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))
    if not result.multi_hand_landmarks:
        return {
            "detected": False,
            "reason": "no_hand_detected",
            "quality": {
                "blur": round(blur_score, 2),
                "brightness": round(brightness, 1),
                "retake": blur_score < BLUR_PASS or not (BRIGHTNESS_LOW <= brightness <= BRIGHTNESS_HIGH),
            },
            "processingTimeMs": round((time.time() - started) * 1000, 1),
        }

    landmarks = result.multi_hand_landmarks[0].landmark
    handedness = result.multi_handedness[0].classification[0].label  # Left/Right

    wrist = landmarks[0]
    index_mcp, middle_mcp, pinky_mcp = landmarks[5], landmarks[9], landmarks[17]
    middle_tip = landmarks[8]

    palm_len = _dist(wrist, middle_mcp)
    palm_width = _dist(index_mcp, pinky_mcp)
    finger_len = _dist(middle_tip, middle_mcp)
    palm_ratio = round(palm_len / max(palm_width, 1e-6), 3)
    finger_ratio = round(finger_len / max(palm_len, 1e-6), 3)

    xs = [p.x for p in landmarks]
    ys = [p.y for p in landmarks]
    pad = 0.06
    x0 = max(0, int((min(xs) - pad) * width))
    x1 = min(width, int((max(xs) + pad) * width))
    y0 = max(0, int((min(ys) - pad) * height))
    y1 = min(height, int((max(ys) + pad) * height))
    roi = frame[y0:y1, x0:x1] if x1 > x0 and y1 > y0 else frame

    blurred_ok = blur_score >= BLUR_PASS
    bright_ok = BRIGHTNESS_LOW <= brightness <= BRIGHTNESS_HIGH

    return {
        "detected": True,
        "handedness": handedness,
        "landmarks": [[round(p.x, 5), round(p.y, 5), round(p.z, 5)] for p in landmarks],
        "palmGeometry": {
            "palmRatio": palm_ratio,
            "fingerRatio": finger_ratio,
            "suggestedPalmShape": _suggest_shape(palm_ratio, finger_ratio),
        },
        "quality": {
            "blur": round(blur_score, 2),
            "brightness": round(brightness, 1),
            "retake": not (blurred_ok and bright_ok),
            "hints": ([] if blurred_ok else ["画面偏模糊，请保持手机稳定"]) + ([] if bright_ok else ["光线不足或过曝，请换到光线充足处"]),
        },
        "roiJpeg": _encode_jpeg_b64(roi),
        "processingTimeMs": round((time.time() - started) * 1000, 1),
    }
