import React from "react";
import "./LoadingSpinner.css";

interface LoadingSpinnerProps {
  message?: string;
  subMessage?: string;
}

const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({
  message = "AI 正在分析掌纹...",
  subMessage = "正在解读生命线、智慧线、感情线...",
}) => {
  return (
    <div className="loading-container fade-in">
      <div className="spinner-ring">
        <div className="ring ring-1" />
        <div className="ring ring-2" />
        <div className="ring ring-3" />
        <div className="palm-icon">
          <svg viewBox="0 0 64 64" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M20 48 Q24 20 32 16 Q40 20 44 48" strokeLinecap="round"/>
            <path d="M23 38 Q32 32 41 38" strokeLinecap="round"/>
            <path d="M26 28 Q32 24 38 28" strokeLinecap="round"/>
          </svg>
        </div>
      </div>
      <h3 className="loading-message">{message}</h3>
      <p className="loading-sub">{subMessage}</p>
    </div>
  );
};

export default LoadingSpinner;
