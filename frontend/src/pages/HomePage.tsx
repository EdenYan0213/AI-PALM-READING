import React from "react";
import { useNavigate } from "react-router-dom";
import "./HomePage.css";

const HomePage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div className="home-page">
      {/* Animated background particles */}
      <div className="home-bg">
        {[...Array(12)].map((_, i) => (
          <div
            key={i}
            className="orb"
            style={{
              width: `${60 + Math.random() * 180}px`,
              height: `${60 + Math.random() * 180}px`,
              left: `${Math.random() * 100}%`,
              top: `${Math.random() * 100}%`,
              animationDelay: `${Math.random() * 5}s`,
              animationDuration: `${6 + Math.random() * 8}s`,
              background: `radial-gradient(circle, ${
                ["rgba(255,111,97,0.12)", "rgba(159,123,255,0.12)", "rgba(62,167,255,0.12)"][i % 3]
              }, transparent 70%)`,
            }}
          />
        ))}
      </div>

      <main className="home-content">
        {/* Logo area */}
        <div className="home-logo">
          <div className="logo-icon">
            <svg viewBox="0 0 120 160" fill="none" stroke="url(#logoGrad)" strokeWidth="2.5">
              <defs>
                <linearGradient id="logoGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stopColor="#ff6f61"/>
                  <stop offset="50%" stopColor="#9f7bff"/>
                  <stop offset="100%" stopColor="#3ea7ff"/>
                </linearGradient>
              </defs>
              {/* Palm */}
              <path d="M25 120 Q30 40 60 28 Q90 40 95 120" strokeLinecap="round"/>
              <path d="M30 90 Q60 78 90 90" strokeLinecap="round"/>
              <path d="M35 65 Q60 55 85 65" strokeLinecap="round"/>
              {/* Fingers */}
              <path d="M38 65 Q30 35 35 15" strokeLinecap="round"/>
              <path d="M50 58 Q48 28 52 10" strokeLinecap="round"/>
              <path d="M60 55 Q62 25 60 8" strokeLinecap="round"/>
              <path d="M72 58 Q78 28 82 12" strokeLinecap="round"/>
              <path d="M82 65 Q90 35 88 18" strokeLinecap="round"/>
            </svg>
          </div>
          <h1 className="home-title">
            <span className="title-line">AI 手相研究所</span>
            <span className="title-sub">AI Palm Reading</span>
          </h1>
          <p className="home-desc">
            通过人工智慧分析掌纹特征，
            <br />
            解读生命线、智慧线与感情线的奥秘
          </p>
        </div>

        {/* Features */}
        <div className="home-features fade-in" style={{ animationDelay: "0.3s" }}>
          <div className="feature-item">
            <div className="feature-dot" style={{ background: "var(--line-life-a)" }} />
            <span>生命线 — 活力与健康</span>
          </div>
          <div className="feature-item">
            <div className="feature-dot" style={{ background: "var(--line-wisdom-a)" }} />
            <span>智慧线 — 思维与才智</span>
          </div>
          <div className="feature-item">
            <div className="feature-dot" style={{ background: "var(--line-love-a)" }} />
            <span>感情线 — 情感与关系</span>
          </div>
          <div className="feature-item">
            <div className="feature-dot" style={{ background: "#ff9e2a" }} />
            <span>命运线 — 事业与人生</span>
          </div>
        </div>

        {/* CTA */}
        <button
          className="btn-primary home-cta fade-in"
          style={{ animationDelay: "0.5s" }}
          onClick={() => navigate("/capture")}
        >
          开始拍摄掌纹
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M5 12h14M12 5l7 7-7 7"/>
          </svg>
        </button>

        {/* Disclaimer */}
        <p className="home-disclaimer">
          仅供娱乐参考，不构成任何专业建议
        </p>
      </main>
    </div>
  );
};

export default HomePage;
