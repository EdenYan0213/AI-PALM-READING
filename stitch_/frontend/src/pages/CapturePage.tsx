import React, { useState, useCallback, useRef, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import "./CapturePage.css";

const CapturePage: React.FC = () => {
  const navigate = useNavigate();
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [stream, setStream] = useState<MediaStream | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [capturedImage, setCapturedImage] = useState<string | null>(null);
  const [facingMode, setFacingMode] = useState<"user" | "environment">("environment");
  const [flash, setFlash] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const startCamera = useCallback(async () => {
    setError(null);
    setCapturedImage(null);

    try {
      const s = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: { ideal: facingMode },
          width: { ideal: 1920 },
          height: { ideal: 1080 },
        },
        audio: false,
      });

      setStream(s);
      if (videoRef.current) {
        videoRef.current.srcObject = s;
      }
    } catch (err: any) {
      const msg =
        err.name === "NotAllowedError"
          ? "请允许使用摄像头访问权限"
          : err.name === "NotFoundError"
          ? "未检测到摄像头设备"
          : `启动失败: ${err.message}`;
      setError(msg);
    }
  }, [facingMode]);

  const stopCamera = useCallback(() => {
    if (stream) {
      stream.getTracks().forEach((t) => t.stop());
      setStream(null);
    }
  }, [stream]);

  useEffect(() => {
    startCamera();
    return () => stopCamera();
  }, [facingMode]);

  const captureFrame = useCallback(() => {
    const video = videoRef.current;
    if (!video || !video.videoWidth) return;

    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    ctx.drawImage(video, 0, 0);
    const dataUrl = canvas.toDataURL("image/jpeg", 0.92);
    setCapturedImage(dataUrl);
    stopCamera();
    setFlash(true);
    setTimeout(() => setFlash(false), 300);
  }, [stopCamera]);

  const handleFileUpload = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = reader.result as string;
      setCapturedImage(dataUrl);
      stopCamera();
    };
    reader.readAsDataURL(file);
  }, [stopCamera]);

  const retake = useCallback(() => {
    setCapturedImage(null);
    startCamera();
  }, [startCamera]);

  const confirmAndAnalyze = useCallback(() => {
    if (capturedImage) {
      navigate("/analysis", { state: { imageData: capturedImage } });
    }
  }, [capturedImage, navigate]);

  const toggleFacing = useCallback(() => {
    stopCamera();
    setFacingMode((p) => (p === "user" ? "environment" : "user"));
  }, [stopCamera]);

  return (
    <div className="capture-page">
      <header className="capture-header">
        <button className="btn-icon" onClick={() => navigate("/")}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M19 12H5M12 19l-7-7 7-7" />
          </svg>
        </button>
        <h1 className="capture-title">拍摄掌纹</h1>
        <button className="btn-icon" onClick={toggleFacing}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M23 19a2 2 0 01-2 2H3a2 2 0 01-2-2V8a2 2 0 012-2h4l2-3h6l2 3h4a2 2 0 012 2z"/>
            <circle cx="12" cy="13" r="4"/>
          </svg>
        </button>
      </header>

      {!capturedImage ? (
        <div className="camera-viewport">
          {error ? (
            <div className="camera-error fade-in">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#ff6f61" strokeWidth="1.5">
                <circle cx="12" cy="12" r="10"/>
                <path d="M12 8v4M12 16h.01"/>
              </svg>
              <p>{error}</p>
              <button className="btn-primary" onClick={startCamera}>重试</button>
            </div>
          ) : (
            <>
              <video
                ref={videoRef}
                autoPlay
                playsInline
                muted
                className="camera-video"
              />
              <div className="palm-guide">
                <div className="guide-ring guide-ring-outer" />
                <div className="guide-ring guide-ring-inner" />
                <div className="guide-hand">
                  <svg viewBox="0 0 200 280" fill="none" stroke="rgba(255,255,255,0.2)" strokeWidth="1.5">
                    <path d="M50 100 Q55 50 100 40 Q145 50 150 100 L155 230 Q150 260 100 265 Q50 260 45 230 Z" stroke="rgba(255,255,255,0.25)" strokeDasharray="6 4"/>
                    <path d="M65 110 Q70 160 85 200 Q90 215 100 230" stroke="rgba(255,111,97,0.4)" strokeWidth="2"/>
                    <path d="M55 130 Q100 125 145 135" stroke="rgba(62,167,255,0.4)" strokeWidth="2"/>
                    <path d="M60 105 Q100 85 140 110" stroke="rgba(255,77,184,0.4)" strokeWidth="2"/>
                    <text x="30" y="70" fill="rgba(255,255,255,0.35)" fontSize="10" fontFamily="inherit">将手掌对准框内</text>
                  </svg>
                </div>
              </div>
              {!stream && (
                <div className="camera-starting">
                  <div className="animate-spin" style={{
                    width: 32, height: 32,
                    border: "3px solid rgba(255,255,255,0.1)",
                    borderTopColor: "#9f7bff",
                    borderRadius: "50%",
                  }} />
                  <p>启动摄像头...</p>
                </div>
              )}
            </>
          )}
        </div>
      ) : (
        <div className="captured-preview fade-in">
          <img src={capturedImage} alt="掌纹照片" className="captured-img" />
        </div>
      )}

      {flash && <div className="flash-overlay" />}

      <div className="capture-actions">
        {!capturedImage ? (
          <>
            {stream && !error && (
              <button className="btn-capture-circle" onClick={captureFrame}>
                <div className="capture-outer">
                  <div className="capture-inner" />
                </div>
              </button>
            )}
            {!stream && !error && (
              <button className="btn-primary" onClick={() => fileInputRef.current?.click()}>
                从相册选择
              </button>
            )}
          </>
        ) : (
          <div className="captured-actions">
            <button className="btn-primary" onClick={confirmAndAnalyze}>
              分析掌纹
            </button>
            <button className="btn-secondary" onClick={retake}>
              重新拍摄
            </button>
          </div>
        )}
      </div>

      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        style={{ display: 'none' }}
        onChange={handleFileUpload}
      />

      <p className="capture-hint">
        请在光线充足的环境下拍摄，手掌自然张开
      </p>
    </div>
  );
};

export default CapturePage;
