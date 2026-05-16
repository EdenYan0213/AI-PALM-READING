import React, { useRef, useCallback, useState } from "react";
import html2canvas from "html2canvas";
import type { AnalyzeResponse } from "../types";
import "./ShareCard.css";

interface ShareCardProps {
  result: AnalyzeResponse;
  imageData?: string;
  mode?: "single" | "cp";
  cpData?: { matchScore: number; comboName: string; partnerName: string };
}

const personalityColorMap: Record<string, string> = {
  INFP: "#9f7bff",
  INFJ: "#3ea7ff",
  ENFP: "#ff9e2a",
  ENFJ: "#ff6f61",
  INTJ: "#3ef3f7",
  INTP: "#7bff9f",
  ENTJ: "#ff4db8",
  ENTP: "#ffd700",
  ISFP: "#ff6f91",
  ISTP: "#5bc0be",
  ESFP: "#ff9f43",
  ESTP: "#ee5a24",
  ISFJ: "#48dbfb",
  ISTJ: "#0abde3",
  ESFJ: "#f368e0",
  ESTJ: "#c44569",
};

const lineColors: Record<string, { color: string; gradient: string }> = {
  love: { color: "#ff4db8", gradient: "linear-gradient(135deg, #ff4db8, #ff8fd8)" },
  wisdom: { color: "#3ea7ff", gradient: "linear-gradient(135deg, #3ea7ff, #7fcbff)" },
  life: { color: "#3ef3f7", gradient: "linear-gradient(135deg, #3ef3f7, #7fffff)" },
};

const lineLabels: Record<string, string> = {
  love: "感情线",
  wisdom: "智慧线",
  life: "生命线",
};

const ShareCard: React.FC<ShareCardProps> = ({ result, imageData, mode = "single", cpData }) => {
  const cardRef = useRef<HTMLDivElement>(null);
  const [exporting, setExporting] = useState(false);
  const [showCard, setShowCard] = useState(false);
  const [cardUrl, setCardUrl] = useState<string | null>(null);

  const overviewLines = result.freeOverview || [];
  const lines = [
    { key: "love", data: overviewLines[0] },
    { key: "wisdom", data: overviewLines[1] },
    { key: "life", data: overviewLines[2] },
  ];

  const today = new Date().toLocaleDateString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });

  const personalities = result.personalityTags || [];
  const rarePersonality = personalities.find((t) => t in personalityColorMap);

  const handleGenerate = useCallback(() => {
    setShowCard(true);
    setCardUrl(null);
  }, []);

  const handleExport = useCallback(async () => {
    if (!cardRef.current || exporting) return;
    setExporting(true);
    try {
      const canvas = await html2canvas(cardRef.current, {
        backgroundColor: "#080d1a",
        scale: 2,
        useCORS: true,
        logging: false,
      });
      const url = canvas.toDataURL("image/png");
      setCardUrl(url);

      // Auto-download
      const link = document.createElement("a");
      link.download = `AI手相研究所_${result.handType}_${today.replace(/\//g, "-")}.png`;
      link.href = url;
      link.click();
    } catch (err) {
      console.error("Export failed:", err);
    } finally {
      setExporting(false);
    }
  }, [result.handType, today, exporting]);

  return (
    <div className="share-card-wrapper">
      {/* Preview button */}
      <div className="share-actions">
        {!showCard ? (
          <button className="btn-share-generate" onClick={handleGenerate}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="18" cy="5" r="3" />
              <circle cx="6" cy="12" r="3" />
              <circle cx="18" cy="19" r="3" />
              <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
              <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
            </svg>
            生成分享卡片
          </button>
        ) : (
          <button className="btn-share-download" onClick={handleExport} disabled={exporting}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4" />
              <polyline points="7 10 12 15 17 10" />
              <line x1="12" y1="15" x2="12" y2="3" />
            </svg>
            {exporting ? "生成中..." : "保存图片"}
          </button>
        )}
      </div>

      {/* Card preview */}
      {showCard && (
        <div className="share-card-preview">
          <div className="share-card" ref={cardRef}>
            {/* Background decoration */}
            <div className="card-bg-grid" />
            <div className="card-corner-tl" />
            <div className="card-corner-tr" />
            <div className="card-corner-bl" />
            <div className="card-corner-br" />
            <div className="card-glow-top" />
            <div className="card-glow-bottom" />

            {/* Content */}
            <div className="card-inner">
              {/* Header */}
              <header className="card-header">
                <div className="card-brand-line">
                  <span className="card-brand-icon">✦</span>
                  <span className="card-brand">AI 手相研究所</span>
                  <span className="card-brand-icon">✦</span>
                </div>
                <p className="card-subtitle">赛博玄学 · 科学聊命</p>
              </header>

              {/* Hand type hero */}
              <section className="card-hero">
                <div className="hand-type-hero">
                  <span className="hand-type-label">HAND TYPE</span>
                  <span className="hand-type-value">{result.handType}</span>
                </div>
                <div className="hero-divider" />
                <div className="personality-tags-row">
                  {personalities.map((tag, i) => (
                    <span
                      key={i}
                      className="card-personality-tag"
                      style={{
                        borderColor: personalityColorMap[tag] || "#9f7bff",
                        color: personalityColorMap[tag] || "#9f7bff",
                        boxShadow: `0 0 12px ${personalityColorMap[tag] || "#9f7bff"}22`,
                      }}
                    >
                      {tag}
                    </span>
                  ))}
                </div>
              </section>

              {/* Palm image */}
              {imageData && (
                <section className="card-image-section">
                  <div className="card-image-frame">
                    <img src={imageData} alt="掌纹" className="card-palm-image" />
                  </div>
                </section>
              )}

              {/* Three lines */}
              <section className="card-lines">
                <h3 className="card-section-label">— 掌纹解读 —</h3>
                <div className="card-lines-grid">
                  {lines.map((item) => {
                    if (!item.data) return null;
                    const style = lineColors[item.key] || lineColors.life;
                    return (
                      <div key={item.key} className="card-line-row">
                        <div className="card-line-dot" style={{ background: style.gradient }} />
                        <div className="card-line-info">
                          <span className="card-line-name" style={{ color: style.color }}>
                            {item.data.lineName || lineLabels[item.key]}
                          </span>
                          {item.data.tags && (
                            <span className="card-line-tags">{item.data.tags}</span>
                          )}
                        </div>
                        <p className="card-line-desc">{item.data.shortInterpretation}</p>
                      </div>
                    );
                  })}
                </div>
              </section>

              {/* Rare mark */}
              {result.rareMark && (
                <section className="card-rare-mark">
                  <div className="rare-mark-glow" />
                  <div className="rare-mark-content">
                    <span className="rare-mark-star">✦</span>
                    <div className="rare-mark-info">
                      <span className="rare-mark-name">{result.rareMark}</span>
                      <span className="rare-mark-rarity">稀有印记 · 仅少数人拥有</span>
                    </div>
                    <span className="rare-mark-star">✦</span>
                  </div>
                </section>
              )}

              {/* Overall summary quote */}
              {result.overallSummary && (
                <section className="card-quote">
                  <p className="card-quote-text">
                    「{result.overallSummary.slice(0, 60)}」
                  </p>
                </section>
              )}

              {/* CP section */}
              {mode === "cp" && cpData && (
                <section className="card-cp-section">
                  <div className="cp-score-ring">
                    <span className="cp-score-number">{cpData.matchScore}%</span>
                    <span className="cp-score-label">匹配度</span>
                  </div>
                  <p className="cp-combo-name">「{cpData.comboName}」</p>
                  <p className="cp-partner">与 {cpData.partnerName} 的宿命合拍</p>
                </section>
              )}

              {/* Slogan */}
              <section className="card-slogan">
                <p>{result.slogan || "你的互联网手相搭子"}</p>
              </section>

              {/* Footer */}
              <footer className="card-footer">
                <div className="card-footer-left">
                  <div className="card-qr-placeholder">
                    <div className="qr-pattern">
                      <svg viewBox="0 0 80 80" fill="none">
                        <rect x="10" y="10" width="25" height="25" rx="2" stroke="rgba(255,255,255,0.6)" strokeWidth="2" fill="none" />
                        <rect x="45" y="10" width="25" height="25" rx="2" stroke="rgba(255,255,255,0.6)" strokeWidth="2" fill="none" />
                        <rect x="10" y="45" width="25" height="25" rx="2" stroke="rgba(255,255,255,0.6)" strokeWidth="2" fill="none" />
                        <rect x="15" y="15" width="5" height="5" fill="rgba(255,255,255,0.6)" />
                        <rect x="25" y="15" width="5" height="5" fill="rgba(255,255,255,0.6)" />
                        <rect x="15" y="25" width="5" height="5" fill="rgba(255,255,255,0.6)" />
                        <rect x="50" y="15" width="5" height="5" fill="rgba(255,255,255,0.6)" />
                        <rect x="50" y="25" width="5" height="5" fill="rgba(255,255,255,0.6)" />
                        <rect x="15" y="50" width="5" height="5" fill="rgba(255,255,255,0.6)" />
                        <rect x="15" y="60" width="5" height="5" fill="rgba(255,255,255,0.6)" />
                        <circle cx="62" cy="62" r="10" stroke="rgba(159,123,255,0.8)" strokeWidth="1.5" fill="none" />
                        <circle cx="62" cy="62" r="4" fill="rgba(159,123,255,0.4)" />
                        <path d="M40 35 L40 45 M35 40 L45 40" stroke="rgba(255,255,255,0.3)" strokeWidth="1" />
                      </svg>
                    </div>
                  </div>
                  <div className="card-footer-text">
                    <p className="card-cta">扫码测你的专属手相</p>
                    <p className="card-date">{today}</p>
                  </div>
                </div>
                {rarePersonality && (
                  <div
                    className="card-mbti-badge"
                    style={{
                      borderColor: personalityColorMap[rarePersonality],
                      color: personalityColorMap[rarePersonality],
                    }}
                  >
                    {rarePersonality}
                  </div>
                )}
              </footer>
            </div>
          </div>

          {/* Preview tip */}
          <p className="share-tip">
            💡 点击「保存图片」即可保存到相册，分享到朋友圈或发送给朋友
          </p>
        </div>
      )}
    </div>
  );
};

export default ShareCard;
