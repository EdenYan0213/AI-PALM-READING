import React, { useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { unlockDeep, queryRareMark } from "../services/api";
import type { AnalyzeResponse } from "../types";
import ShareCard from "../components/ShareCard";
import "./ResultPage.css";

const ResultPage: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const resultRef = useRef<HTMLDivElement>(null);

  const result = location.state?.result as AnalyzeResponse | undefined;
  const imageData = location.state?.imageData as string | undefined;
  const [unlocked, setUnlocked] = useState(false);
  const [deepSections, setDeepSections] = useState<{ title: string; detail: string; cyberTip: string }[]>([]);
  const [unlocking, setUnlocking] = useState(false);

  if (!result) {
    return (
      <div className="result-error">
        <h2>未找到分析结果</h2>
        <button className="btn-primary" onClick={() => navigate("/capture")}>
          重新拍摄
        </button>
      </div>
    );
  }

  const personalityColors = ["#ff6f61", "#9f7bff", "#3ea7ff", "#3ef3f7", "#ff9e2a", "#ff4db8"];

  const overviewLines = result.freeOverview || [];
  const lines = [
    { key: "love", label: "感情线", data: overviewLines[0], color: "#ff4db8" },
    { key: "wisdom", label: "智慧线", data: overviewLines[1], color: "#3ea7ff" },
    { key: "life", label: "生命线", data: overviewLines[2], color: "#3ef3f7" },
  ];

  const handleUnlock = async () => {
    if (!result.sessionId || unlocking) return;
    setUnlocking(true);
    try {
      const deep = await unlockDeep(result.sessionId);
      setUnlocked(true);
      if (deep.sections) {
        setDeepSections(deep.sections);
      }
    } catch {
      setUnlocking(false);
    }
  };

  return (
    <div className="result-page" ref={resultRef}>
      <header className="result-header">
        <button className="btn-icon" onClick={() => navigate("/capture")}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M19 12H5M12 19l-7-7 7-7" />
          </svg>
        </button>
        <h1 className="result-title">掌纹分析报告</h1>
        <button className="btn-icon" onClick={() => window.print()}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="18" cy="5" r="3" />
            <circle cx="6" cy="12" r="3" />
            <circle cx="18" cy="19" r="3" />
            <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
            <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
          </svg>
        </button>
      </header>

      <section className="result-overview slide-up">
        <div className="hand-shape-badge">
          <span className="badge-label">手型</span>
          <span className="badge-value">{result.handType}</span>
        </div>
        <div className="personality-tags">
          {(result.personalityTags || []).map((tag, i) => (
            <span
              key={i}
              className="personality-tag fade-in"
              style={{
                animationDelay: `${i * 0.1}s`,
                borderColor: personalityColors[i % personalityColors.length],
                color: personalityColors[i % personalityColors.length],
              }}
            >
              {tag}
            </span>
          ))}
        </div>
      </section>

      {imageData && (
        <section className="result-image-section slide-up" style={{ animationDelay: "0.15s" }}>
          <img src={imageData} alt="掌纹原图" className="result-image" />
        </section>
      )}

      {result.overallSummary && (
        <section className="result-summary slide-up" style={{ animationDelay: "0.2s" }}>
          <h2 className="section-title">综合解读</h2>
          <div className="summary-card glass-panel">
            <p className="summary-text">{result.overallSummary}</p>
          </div>
        </section>
      )}

      <section className="result-lines slide-up" style={{ animationDelay: "0.25s" }}>
        <h2 className="section-title">掌纹解读</h2>
        {lines.map((item, i) => item.data && (
          <div
            key={item.key}
            className="line-card glass-panel fade-in"
            style={{ animationDelay: `${(i + 1) * 0.15}s` }}
          >
            <div className="line-card-header">
              <div className="line-indicator" style={{ background: item.color }} />
              <h3 className="line-name">{item.data.lineName || item.label}</h3>
              {item.data.tags && <span className="line-tags">{item.data.tags}</span>}
            </div>
            <p className="line-desc">{item.data.shortInterpretation}</p>
            {(unlocked && item.data.detailInterpretation) && (
              <div className="line-detail">
                <p>{item.data.detailInterpretation}</p>
                <div className="line-tips">
                  {item.data.actionableAdvice && (
                    <span className="tip-badge">💡 {item.data.actionableAdvice}</span>
                  )}
                  {item.data.timeWindow && (
                    <span className="tip-badge time-badge">🕐 {item.data.timeWindow}</span>
                  )}
                </div>
              </div>
            )}
          </div>
        ))}
      </section>

      {(result.rareMark) && (
        <section className="rare-mark-section slide-up" style={{ animationDelay: "0.4s" }}>
          <div className="rare-mark-card glass-panel">
            <h3 className="section-title">稀有印记</h3>
            <p className="rare-mark-name">{result.rareMark}</p>
            <p className="rare-mark-desc">{result.teaser}</p>
          </div>
        </section>
      )}

      {(result.careerTip || result.loveTip || result.healthTip || result.luckyPeriod) && (
        <section className="result-tips slide-up" style={{ animationDelay: "0.45s" }}>
          <h2 className="section-title">专属建议</h2>
          {result.careerTip && (
            <div className="tip-card glass-panel">
              <h4>💼 职业建议</h4>
              <p>{result.careerTip}</p>
            </div>
          )}
          {result.loveTip && (
            <div className="tip-card glass-panel">
              <h4>❤️ 感情建议</h4>
              <p>{result.loveTip}</p>
            </div>
          )}
          {result.healthTip && (
            <div className="tip-card glass-panel">
              <h4>🏃 健康建议</h4>
              <p>{result.healthTip}</p>
            </div>
          )}
          {result.luckyPeriod && (
            <div className="tip-card glass-panel">
              <h4>🍀 幸运时段</h4>
              <p>{result.luckyPeriod}</p>
            </div>
          )}
        </section>
      )}

      {deepSections.length > 0 && (
        <section className="deep-sections slide-up" style={{ animationDelay: "0.5s" }}>
          <h2 className="section-title">深度趋势报告</h2>
          {deepSections.map((s, i) => (
            <div key={i} className="deep-card glass-panel">
              <h4>{s.title}</h4>
              <p>{s.detail}</p>
              <p className="cyber-tip">💡 {s.cyberTip}</p>
            </div>
          ))}
        </section>
      )}

      <section className="result-actions slide-up" style={{ animationDelay: "0.55s" }}>
        {!unlocked ? (
          <button className="btn-primary btn-unlock" onClick={handleUnlock} disabled={unlocking}>
            {unlocking ? "解锁中，请等待15秒..." : "解锁完整报告"}
          </button>
        ) : (
          <span className="unlock-badge">✅ 已解锁完整报告</span>
        )}
        <button className="btn-secondary" onClick={() => navigate("/capture")}>
          再测一次
        </button>
      </section>

      {/* Share Card */}
      <section className="result-share slide-up" style={{ animationDelay: "0.6s" }}>
        <ShareCard result={result} imageData={imageData} mode="single" />
      </section>

      <footer className="result-footer">
        <span className="result-session">{result.sessionId}</span>
        {result.llmUsed && (
          <span className="llm-badge">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
            </svg>
            AI 深度解读
          </span>
        )}
        {!result.llmUsed && result.llmEnhancedAvailable && (
          <span className="llm-badge llm-badge-fallback">基础解读 · 可解锁深度报告</span>
        )}
        {!result.llmUsed && !result.llmEnhancedAvailable && (
          <span className="llm-badge llm-badge-fallback">增强基础版</span>
        )}
      </footer>
    </div>
  );
};

export default ResultPage;
