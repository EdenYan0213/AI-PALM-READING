import '/src/styles/main.css';
import { store } from '/src/shared/js/store.js';

// PRD §6.2：分析页是 3-5 秒的纯动效页（原版会在此同步等待一次服务端 LLM 校验，
// 现校验已并入 /palm/analyze 的同一次调用，本页不再发请求）。

const steps = [
  '正在扫描掌型编码...',
  '正在破译感情线波段...',
  '正在交叉比对智慧线标签...',
  '正在捕获稀有印记...',
  '正在生成赛博解读文案...'
];

const progressPercent = document.getElementById('progressPercent');
const progressFill = document.getElementById('progressFill');
const statusPrev = document.getElementById('statusPrev');
const statusCurrent = document.getElementById('statusCurrent');
const statusNext = document.getElementById('statusNext');
const scanPreviewImage = document.getElementById('scanPreviewImage');
const errorPanel = document.getElementById('errorPanel');
const errorReason = document.getElementById('errorReason');
const retryButton = document.getElementById('retryButton');

let progress = 12;
let finished = false;
let tick = null;
const imageData = store.getCapturedImage() || '';
if (!imageData) {
  statusCurrent.textContent = '请先拍照或上传手掌照片';
  setTimeout(() => {
    window.location.href = '/capture.html';
  }, 900);
} else {
  scanPreviewImage.src = imageData;
}

const paintStatus = () => {
  const index = Math.min(steps.length - 1, Math.floor((progress / 100) * steps.length));
  statusPrev.textContent = steps[Math.max(0, index - 1)];
  statusCurrent.textContent = steps[index];
  statusNext.textContent = steps[Math.min(steps.length - 1, index + 1)];
};

const renderProgress = () => {
  const value = Math.round(progress);
  progressPercent.textContent = value + '%';
  progressFill.style.width = value + '%';
  paintStatus();
};

const finishAndContinue = () => {
  if (finished) {
    return;
  }
  finished = true;
  if (tick) {
    clearInterval(tick);
  }
  progress = 100;
  renderProgress();
  setTimeout(() => {
    window.location.href = '/trace.html?from=analysis';
  }, 420);
};

const showError = (message) => {
  if (finished) {
    return;
  }
  if (tick) {
    clearInterval(tick);
  }
  errorReason.textContent = message;
  errorPanel.classList.remove('hidden');
};

// 进度条约 4 秒推进到 100% 后自动进入描纹页（PRD：分析页 3-5 秒内完成并跳转）。
const startProgressTick = () => {
  if (tick) {
    clearInterval(tick);
  }
  tick = setInterval(() => {
    if (finished) {
      return;
    }
    progress += Math.random() * 3 + 1.2;
    if (progress >= 100) {
      finishAndContinue();
      return;
    }
    renderProgress();
  }, 140);
};

const runIntroAnimation = () => {
  errorPanel.classList.add('hidden');
  startProgressTick();
};

retryButton.addEventListener('click', () => {
  if (!imageData) {
    window.location.href = '/capture.html';
    return;
  }
  runIntroAnimation();
});

if (imageData) {
  paintStatus();
  runIntroAnimation();
}
