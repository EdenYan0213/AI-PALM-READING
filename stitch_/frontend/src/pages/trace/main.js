import '/src/styles/main.css';
import { ensureUserId, post, postSse } from '/src/shared/js/api.js';
import { store } from '/src/shared/js/store.js';

const query = new URLSearchParams(window.location.search);
const recordFlow = query.get('recordFlow') || 'single';

const lineConfigs = {
  lifeLine: {
    label: '生命线',
    hint: '跟着光的指引，用指尖描摹你的生命线。',
    gradient: ['#ff6f61', '#ff9e2a'],
    guideStroke: 'rgba(255,146,146,0.78)',
    guide: 'M238,1068 C210,880 204,734 252,608 C290,510 344,442 392,404'
  },
  wisdomLine: {
    label: '智慧线',
    hint: '沿着星光虚线，描摹你的智慧线走势。',
    gradient: ['#3ea7ff', '#3ef3f7'],
    guideStroke: 'rgba(126,198,255,0.8)',
    guide: 'M218,598 C308,616 436,638 556,682 C654,718 746,752 816,784'
  },
  loveLine: {
    label: '感情线',
    hint: '请轻描感情线，让AI读取你的情绪结构。',
    gradient: ['#ff4db8', '#9f7bff'],
    guideStroke: 'rgba(255,150,224,0.82)',
    guide: 'M226,460 C334,404 454,382 598,390 C696,396 770,424 836,468'
  }
};

const palmImage = document.getElementById('palmImage');
const traceCanvas = document.getElementById('traceCanvas');
const guidePath = document.getElementById('guidePath');
const guideHint = document.getElementById('guideHint');
const lineTabs = document.getElementById('lineTabs');
const clearCurrentButton = document.getElementById('clearCurrentButton');
const confirmButton = document.getElementById('confirmButton');
const skipButton = document.getElementById('skipButton');
const submitStatus = document.getElementById('submitStatus');
const progressText = document.getElementById('progressText');

const imageData = store.getCapturedImage() || '';
if (!imageData) {
  window.location.href = '/capture.html';
}
palmImage.src = imageData;

const ctx = traceCanvas.getContext('2d');
const traces = {
  lifeLine: [],
  wisdomLine: [],
  loveLine: []
};
const particles = [];
let currentLine = 'lifeLine';
let drawing = false;
let lastPoint = null;

const resizeCanvas = () => {
  const rect = traceCanvas.getBoundingClientRect();
  traceCanvas.width = Math.max(1, Math.round(rect.width * window.devicePixelRatio));
  traceCanvas.height = Math.max(1, Math.round(rect.height * window.devicePixelRatio));
  ctx.setTransform(window.devicePixelRatio, 0, 0, window.devicePixelRatio, 0, 0);
  redrawAll();
};

const setGuide = () => {
  const config = lineConfigs[currentLine];
  guideHint.textContent = config.hint;
  guidePath.setAttribute('d', config.guide);
  guidePath.setAttribute('stroke', config.guideStroke);
  lineTabs.querySelectorAll('button').forEach((button) => {
    button.classList.toggle('active', button.dataset.line === currentLine);
  });
};

const toCanvasPoint = (event) => {
  const rect = traceCanvas.getBoundingClientRect();
  return {
    x: event.clientX - rect.left,
    y: event.clientY - rect.top,
    t: Date.now()
  };
};

const drawSegment = (from, to, key) => {
  const colors = lineConfigs[key].gradient;
  ctx.save();
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  ctx.lineWidth = 5;
  const gradient = ctx.createLinearGradient(from.x, from.y, to.x, to.y);
  gradient.addColorStop(0, colors[0]);
  gradient.addColorStop(1, colors[1]);
  ctx.strokeStyle = gradient;
  ctx.shadowBlur = 18;
  ctx.shadowColor = colors[0];
  ctx.beginPath();
  ctx.moveTo(from.x, from.y);
  ctx.lineTo(to.x, to.y);
  ctx.stroke();
  ctx.restore();
};

const spawnParticles = (point, key) => {
  const colors = lineConfigs[key].gradient;
  for (let i = 0; i < 3; i++) {
    particles.push({
      x: point.x,
      y: point.y,
      vx: (Math.random() - 0.5) * 0.9,
      vy: -0.4 - Math.random() * 0.8,
      life: 18 + Math.floor(Math.random() * 12),
      color: colors[Math.floor(Math.random() * colors.length)]
    });
  }
};

const drawParticles = () => {
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.x += p.vx;
    p.y += p.vy;
    p.vy += 0.03;
    p.life -= 1;
    ctx.save();
    ctx.globalAlpha = Math.max(0, p.life / 30);
    ctx.fillStyle = p.color;
    ctx.beginPath();
    ctx.arc(p.x, p.y, 1.8, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
    if (p.life <= 0) {
      particles.splice(i, 1);
    }
  }
};

const redrawAll = () => {
  ctx.clearRect(0, 0, traceCanvas.width, traceCanvas.height);
  Object.keys(traces).forEach((key) => {
    const points = traces[key];
    for (let i = 1; i < points.length; i++) {
      drawSegment(points[i - 1], points[i], key);
    }
  });
};

const animate = () => {
  redrawAll();
  drawParticles();
  window.requestAnimationFrame(animate);
};

const vibrateOnce = () => {
  try {
    if (window.wx && typeof window.wx.vibrateShort === 'function') {
      window.wx.vibrateShort();
      return;
    }
  } catch (e) {
    // Ignore wx runtime errors and fallback to web vibrate.
  }
  if (navigator.vibrate) {
    navigator.vibrate(12);
  }
};

const addPoint = (point) => {
  const points = traces[currentLine];
  if (points.length > 0) {
    const prev = points[points.length - 1];
    const distance = Math.hypot(prev.x - point.x, prev.y - point.y);
    if (distance < 3) {
      return;
    }
    drawSegment(prev, point, currentLine);
  }
  points.push(point);
  spawnParticles(point, currentLine);
};

const updateProgress = () => {
  const completed = Object.values(traces).filter((line) => line.length >= 8).length;
  progressText.textContent = '已完成 ' + completed + ' / 3';
  confirmButton.disabled = completed < 3;
  confirmButton.classList.toggle('hidden', completed < 3);
};

const onPointerDown = (event) => {
  event.preventDefault();
  drawing = true;
  traceCanvas.setPointerCapture(event.pointerId);
  lastPoint = toCanvasPoint(event);
  vibrateOnce();
  addPoint(lastPoint);
  updateProgress();
};

const onPointerMove = (event) => {
  if (!drawing) {
    return;
  }
  event.preventDefault();
  const point = toCanvasPoint(event);
  lastPoint = point;
  addPoint(point);
  updateProgress();
};

const onPointerUp = (event) => {
  if (!drawing) {
    return;
  }
  drawing = false;
  traceCanvas.releasePointerCapture(event.pointerId);
  if (lastPoint) {
    spawnParticles(lastPoint, currentLine);
  }
  updateProgress();
};

const exportTraces = () => {
  const toPayloadLine = (line) => line.map((point) => ({
    x: Number(point.x.toFixed(2)),
    y: Number(point.y.toFixed(2)),
    t: point.t
  }));
  return {
    lifeLine: toPayloadLine(traces.lifeLine),
    wisdomLine: toPayloadLine(traces.wisdomLine),
    loveLine: toPayloadLine(traces.loveLine)
  };
};

const showRejectionLink = () => {
  if (document.getElementById('recaptureLink')) {
    return;
  }
  const link = document.createElement('a');
  link.id = 'recaptureLink';
  link.href = '/capture.html';
  link.textContent = '重新拍照';
  link.className = 'ml-2 underline text-pink-200';
  submitStatus.insertAdjacentElement('afterend', link);
};

const removeRejectionLink = () => {
  const link = document.getElementById('recaptureLink');
  if (link) {
    link.remove();
  }
};

// 流式接口：stage/progress 事件实时呈现真实进度；不支持时返回 null 由调用方回退普通 POST。
const analyzeViaStream = (body) =>
  postSse('/palm/analyze/stream', body, (eventName, payload) => {
    if (eventName === 'stage') {
      submitStatus.textContent = payload.message || '正在分析...';
    } else if (eventName === 'progress') {
      submitStatus.textContent = '正在生成专属解读...（已生成 ' + payload.generatedChars + ' 字）';
    } else if (eventName === 'error') {
      const error = new Error(payload.message || '生成失败');
      error.code = payload.code;
      throw error;
    }
  });

const submitAnalyze = async (withTraces) => {
  confirmButton.disabled = true;
  skipButton.disabled = true;
  clearCurrentButton.disabled = true;
  removeRejectionLink();
  submitStatus.textContent = recordFlow === 'weekly'
    ? '正在保存本周记录...'
    : '阶段 1/3 · 正在量化掌纹特征...';

  try {
    store.clearAnalyzeError();
    let payload = null;
    if (recordFlow === 'weekly') {
      const weeklyBody = {
        userId: await ensureUserId(),
        recordMode: withTraces ? 'full' : 'quick',
        imageData,
        recordDate: new Date().toISOString().slice(0, 10)
      };
      if (withTraces) {
        weeklyBody.traces = exportTraces();
      }
      payload = await post('/record/weekly', weeklyBody);
    } else {
      const body = {
        source: 'camera',
        handSide: 'left',
        gender: 'unknown',
        imageData,
        recordMode: 'single'
      };
      if (withTraces) {
        body.traces = exportTraces();
      }
      payload = await analyzeViaStream(body);
      if (!payload) {
        payload = await post('/palm/analyze', body);
      }
    }

    if (!payload || !(payload.sessionId || payload.recordId)) {
      throw new Error('empty analyze payload');
    }

    if (recordFlow === 'weekly') {
      store.setWeeklyRecord(payload);
    } else {
      store.setReport(payload);
    }
    submitStatus.textContent = '已完成，正在进入报告...';
    setTimeout(function () {
      if (recordFlow === 'weekly') {
        window.location.href = '/weekly.html?from=trace';
      } else {
        window.location.href = '/report.html?from=trace';
      }
    }, 320);
  } catch (error) {
    const errorText = String(error && error.message ? error.message : error);
    store.setAnalyzeError(errorText);
    if (error && (error.code === 'IMAGE_REJECTED' || error.status === 422)) {
      submitStatus.textContent = '这张照片不像手掌正面，请重新拍摄一张光线充足的手掌照片。';
      showRejectionLink();
    } else if (error && error.name === 'AbortError') {
      submitStatus.textContent = '生成超时了，请检查网络后重试。';
    } else {
      submitStatus.textContent = '生成失败，请稍后重试';
    }
    confirmButton.disabled = false;
    skipButton.disabled = false;
    clearCurrentButton.disabled = false;
  }
};

lineTabs.addEventListener('click', function (event) {
  const button = event.target.closest('button[data-line]');
  if (!button) {
    return;
  }
  currentLine = button.dataset.line;
  setGuide();
});

clearCurrentButton.addEventListener('click', function () {
  traces[currentLine] = [];
  updateProgress();
});

skipButton.addEventListener('click', function () {
  submitAnalyze(false);
});

confirmButton.addEventListener('click', function () {
  submitAnalyze(true);
});

traceCanvas.addEventListener('pointerdown', onPointerDown);
traceCanvas.addEventListener('pointermove', onPointerMove);
traceCanvas.addEventListener('pointerup', onPointerUp);
traceCanvas.addEventListener('pointercancel', onPointerUp);

window.addEventListener('resize', resizeCanvas);

resizeCanvas();
setGuide();
updateProgress();
animate();
