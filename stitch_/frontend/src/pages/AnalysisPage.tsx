import React, { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { analyzePalm } from "../services/api";
import LoadingSpinner from "../components/LoadingSpinner";
import type { AnalyzeResponse } from "../types";
import "./AnalysisPage.css";

const AnalysisPage: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const imageData = location.state?.imageData as string | undefined;

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<AnalyzeResponse | null>(null);
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    if (!imageData) {
      navigate("/capture", { replace: true });
      return;
    }

    let cancelled = false;

    const doAnalysis = async () => {
      setLoading(true);
      setError(null);

      try {
        const res = await analyzePalm(imageData);
        if (!cancelled) {
          if (res && res.sessionId) {
            setResult(res);
            navigate("/result", { state: { result: res, imageData }, replace: true });
          } else {
            setError("分析结果无效，请重试");
            setLoading(false);
          }
        }
      } catch (err: any) {
        if (!cancelled) {
          setError(err.message || "网络错误，请检查连接");
          setLoading(false);
        }
      }
    };

    doAnalysis();
    return () => { cancelled = true; };
  }, [imageData, navigate, retryCount]);

  if (!imageData) return null;

  return (
    <div className="analysis-page">
      {loading && (
        <LoadingSpinner
          message="AI 正在分析掌纹..."
          subMessage="正在识别掌纹特征与布局"
        />
      )}

      {error && (
        <div className="analysis-error fade-in">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#ff6f61" strokeWidth="1.5">
            <circle cx="12" cy="12" r="10"/>
            <path d="M12 8v4M12 16h.01"/>
          </svg>
          <h3>分析出错</h3>
          <p>{error}</p>
          <div className="analysis-error-actions">
            <button className="btn-secondary" onClick={() => navigate("/capture")}>
              重新拍摄
            </button>
            <button className="btn-primary" onClick={() => setRetryCount((c) => c + 1)}>
              重试
            </button>
          </div>
        </div>
      )}

      {/* Preview image while loading */}
      {loading && (
        <div className="analysis-preview">
          <img src={imageData} alt="掌纹预览" className="analysis-thumb" />
        </div>
      )}
    </div>
  );
};

export default AnalysisPage;
