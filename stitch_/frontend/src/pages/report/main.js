import '/src/styles/main.css';
import { post, track } from '/src/shared/js/api.js';
import { store, clearAfterReportViewed } from '/src/shared/js/store.js';

const handTypeTitle = document.getElementById('handTypeTitle');
const sessionText = document.getElementById('sessionText');
const tag1 = document.getElementById('tag1');
const tag2 = document.getElementById('tag2');
const tag3 = document.getElementById('tag3');
const heartLineText = document.getElementById('heartLineText');
const wisdomLineText = document.getElementById('wisdomLineText');
const lifeLineText = document.getElementById('lifeLineText');
const rareMarkName = document.getElementById('rareMarkName');
const rareMarkDesc = document.getElementById('rareMarkDesc');
const wechatText = document.getElementById('wechatText');
const quotaText = document.getElementById('quotaText');
const llmBadge = document.getElementById('llmBadge');
const traceStatusText = document.getElementById('traceStatusText');
const linesSummary = document.getElementById('linesSummary');
const unlockHintText = document.getElementById('unlockHintText');
const unlockButton = document.getElementById('unlockDeepReportButton');
const adUnlockStatus = document.getElementById('adUnlockStatus');
const rareMarkButton = document.getElementById('rareMarkButton');
const deepSectionContainer = document.getElementById('deepSectionContainer');
const capturedPalmCard = document.getElementById('capturedPalmCard');
const capturedPalmPreview = document.getElementById('capturedPalmPreview');

// 私域引导弹层（PRD §6.4）
const rareMarkModal = document.getElementById('rareMarkModal');
const modalMarkName = document.getElementById('modalMarkName');
const modalWechatId = document.getElementById('modalWechatId');
const modalQuotaText = document.getElementById('modalQuotaText');
const copyWechatButton = document.getElementById('copyWechatButton');
const rareMarkModalClose = document.getElementById('rareMarkModalClose');

let sessionId = null;
let overview = [];
let unlocked = false;

const goBackToCapture = (message) => {
  unlockHintText.textContent = message;
  setTimeout(() => {
    window.location.href = '/capture.html';
  }, 1000);
};

const showPreview = () => {
  if (overview.length < 1) {
    heartLineText.textContent = '暂无可显示内容';
    return;
  }
  heartLineText.textContent = overview[0]?.shortInterpretation || '暂无可显示内容';
  if (!unlocked) {
    wisdomLineText.textContent = '此部分为进阶解读，等待15秒后可解锁完整解析。';
    lifeLineText.textContent = '此部分为进阶解读，等待15秒后可解锁完整解析。';
  }
};

const showFullOverview = () => {
  unlocked = true;
  if (overview.length >= 3) {
    wisdomLineText.textContent = overview[1]?.shortInterpretation || wisdomLineText.textContent;
    lifeLineText.textContent = overview[2]?.shortInterpretation || lifeLineText.textContent;
  }
};

const renderDeepSections = (sections) => {
  deepSectionContainer.innerHTML = '';
  if (!Array.isArray(sections) || sections.length === 0) {
    return;
  }
  sections.forEach((section) => {
    const card = document.createElement('div');
    card.className = 'card rounded-2xl p-5';
    card.innerHTML =
      '<h4 class="font-semibold text-pink-700">' + (section.title || '深度趋势') + '</h4>' +
      '<p class="mt-2 text-sm leading-6">' + (section.detail || '') + '</p>' +
      '<p class="mt-2 text-xs text-pink-700">赛博建议: ' + (section.cyberTip || '') + '</p>';
    deepSectionContainer.appendChild(card);
  });
};

// PalmFeatureSet v1.1：展示描摹主线的确定性几何特征
const renderLinesSummary = (fs) => {
  const lines = fs && Array.isArray(fs.lines) ? fs.lines : [];
  if (lines.length === 0) {
    return;
  }
  linesSummary.innerHTML = '';
  lines.forEach((line) => {
    const chip = document.createElement('span');
    chip.className = 'text-[11px] px-2 py-1 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-100';
    chip.textContent = line.lineName + ' · ' + line.length + ' / ' + line.curvature + ' / ' + line.continuity;
    linesSummary.appendChild(chip);
  });
  linesSummary.classList.remove('hidden');
};

const loadReport = () => {
  const capturedImage = store.getCapturedImage();
  if (capturedImage) {
    capturedPalmPreview.src = capturedImage;
    capturedPalmCard.classList.remove('hidden');
  }

  const payload = store.getReport();
  if (!payload) {
    const analyzeError = store.getAnalyzeError();
    goBackToCapture(analyzeError ? '上次模型分析失败，请重新拍照后重试。' : '请先拍照并完成分析，再查看报告。');
    return;
  }
  if (!payload.sessionId) {
    goBackToCapture('分析结果无效，请重新拍照。');
    return;
  }

  sessionId = payload.sessionId;
  sessionText.textContent = payload.sessionId;
  handTypeTitle.textContent = payload.handType || '未知手型';

  // PalmFeatureSet 判定依据（确定性识别的可见性, TD §13.3）
  const featureBasisText = document.getElementById('featureBasisText');
  const fs = payload.featureSet;
  if (fs && fs.palmShape) {
    const basis = fs.palmShape.basis;
    if (basis && basis.palmRatio != null) {
      featureBasisText.textContent = '确定性识别 · 掌长宽比 ' + basis.palmRatio.toFixed(2) + ' · 指长掌长比 ' + (basis.fingerRatio ?? '-');
      featureBasisText.classList.remove('hidden');
    } else if (fs.source === 'heuristic_hash') {
      featureBasisText.textContent = '本次为基础识别模式，接入感知引擎后依据会更精确';
      featureBasisText.classList.remove('hidden');
    }
  }

  if (Array.isArray(payload.personalityTags)) {
    tag1.textContent = payload.personalityTags[0] || tag1.textContent;
    tag2.textContent = payload.personalityTags[1] || tag2.textContent;
    tag3.textContent = payload.personalityTags[2] || tag3.textContent;
  }

  if (Array.isArray(payload.freeOverview)) {
    overview = payload.freeOverview;
  }
  showPreview();

  renderLinesSummary(fs);

  if (payload.traceConfirmed === true) {
    const summary = payload.traceFeatureSummary || '已进行掌纹共同确认，报告依据你的描绘轨迹生成。';
    traceStatusText.textContent = summary;
    traceStatusText.className = 'mt-2 text-xs px-3 py-2 rounded-xl bg-emerald-100 text-emerald-800';
  } else {
    traceStatusText.textContent = '未经确认，仅供参考。你可返回重画掌纹以获得更个性化解读。';
    traceStatusText.className = 'mt-2 text-xs px-3 py-2 rounded-xl bg-amber-100 text-amber-800';
  }

  if (payload.rareMark) {
    rareMarkName.textContent = payload.rareMark;
    rareMarkDesc.textContent = payload.teaser || rareMarkDesc.textContent;
  }

  if (payload.llmUsed === true) {
    llmBadge.textContent = '模型实时生成';
    llmBadge.className = 'text-xs px-3 py-1 rounded-full bg-emerald-100 text-emerald-700';
    unlockHintText.textContent = '当前仅展示部分内容，点击按钮等待15秒后可解锁完整报告。';
  } else {
    const statusText = payload.llmStatus ? ('状态: ' + payload.llmStatus) : '状态: fallback';
    llmBadge.textContent = '模型通道波动';
    llmBadge.className = 'text-xs px-3 py-1 rounded-full bg-amber-100 text-amber-700';
    unlockHintText.textContent = '本次使用增强基础版报告，' + statusText + '。可重新拍照再试一次模型生成。';
  }

  // 拍照 base64 在报告渲染后即完成使命，及时释放 localStorage 配额
  clearAfterReportViewed();
};

unlockButton.addEventListener('click', async () => {
  if (!sessionId) {
    adUnlockStatus.textContent = '当前会话无效，请重新分析';
    return;
  }

  unlockButton.disabled = true;
  adUnlockStatus.textContent = '解锁计时中，请等待15秒...';

  try {
    await new Promise((resolve) => setTimeout(resolve, 15000));
    const payload = await post('/palm/unlock-deep', { sessionId });
    showFullOverview();
    renderDeepSections(payload.sections);
    unlockButton.textContent = '已解锁完整报告';
    unlockButton.classList.add('opacity-70', 'cursor-not-allowed');
    adUnlockStatus.textContent = '解锁成功';
  } catch (e) {
    unlockButton.disabled = false;
    adUnlockStatus.textContent = '解锁失败，请稍后重试';
  }
});

const openRareMarkModal = async () => {
  if (!sessionId) {
    return;
  }
  try {
    const payload = await post('/palm/rare-mark', { sessionId });
    rareMarkName.textContent = payload.markName || rareMarkName.textContent;
    wechatText.textContent = '企业微信: ' + (payload.wechatId || 'CyberPalm-Master');
    quotaText.textContent = '本日剩余查询名额: ' + (payload.remainQuota ?? '--') + ' 次';

    modalMarkName.textContent = payload.markName || rareMarkName.textContent;
    modalWechatId.textContent = payload.wechatId || 'CyberPalm-Master';
    modalQuotaText.textContent = '本日剩余查询名额: ' + (payload.remainQuota ?? '--') + ' 次';
    rareMarkModal.classList.remove('hidden');
  } catch (e) {
    // 查询失败时保留现有展示数据
  }
};

rareMarkButton.addEventListener('click', openRareMarkModal);

copyWechatButton.addEventListener('click', async () => {
  const wechatId = modalWechatId.textContent || 'CyberPalm-Master';
  try {
    await navigator.clipboard.writeText(wechatId);
    copyWechatButton.textContent = '已复制，去添加吧';
  } catch (error) {
    // 降级：选中文本便于手动复制
    window.prompt('请手动复制企业微信号：', wechatId);
  }
});

rareMarkModalClose.addEventListener('click', () => rareMarkModal.classList.add('hidden'));
rareMarkModal.addEventListener('click', (event) => {
  if (event.target === rareMarkModal) {
    rareMarkModal.classList.add('hidden');
  }
});

const shareCardButton = document.getElementById('shareCardButton');
const shareCardModal = document.getElementById('shareCardModal');
const shareCardCanvas = document.getElementById('shareCardCanvas');
const shareCardDownload = document.getElementById('shareCardDownload');
const shareCardClose = document.getElementById('shareCardClose');

const drawRoundedRect = (ctx, x, y, w, h, r) => {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
};

const wrapText = (ctx, text, x, y, maxWidth, lineHeight) => {
  let line = '';
  let currentY = y;
  for (let i = 0; i < text.length; i++) {
    const test = line + text[i];
    if (ctx.measureText(test).width > maxWidth && line.length > 0) {
      ctx.fillText(line, x, currentY);
      line = text[i];
      currentY += lineHeight;
    } else {
      line = test;
    }
  }
  if (line) {
    ctx.fillText(line, x, currentY);
    currentY += lineHeight;
  }
  return currentY;
};

const drawShareCard = () => {
  const ctx = shareCardCanvas.getContext('2d');
  const w = shareCardCanvas.width;
  const h = shareCardCanvas.height;

  const bg = ctx.createLinearGradient(0, 0, 0, h);
  bg.addColorStop(0, '#fff5f7');
  bg.addColorStop(1, '#fde3ec');
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, w, h);

  ctx.fillStyle = '#ec4899';
  ctx.font = '600 34px "Plus Jakarta Sans", "Microsoft YaHei", sans-serif';
  ctx.fillText('Palmistry AI 手相研究所', 60, 96);

  // 手掌照片（圆角裁切）
  const capturedImage = store.getCapturedImage() || capturedPalmPreview.src;
  const photoBottom = 420;
  if (capturedImage) {
    const img = new Image();
    img.onload = () => {
      ctx.save();
      drawRoundedRect(ctx, 60, 140, 600, photoBottom - 180, 32);
      ctx.clip();
      const scale = Math.max(600 / img.width, (photoBottom - 180) / img.height);
      const dw = img.width * scale;
      const dh = img.height * scale;
      ctx.drawImage(img, 60 + (600 - dw) / 2, 140 + (photoBottom - 180 - dh) / 2, dw, dh);
      ctx.restore();
    };
    img.src = capturedImage;
  } else {
    ctx.fillStyle = '#fbcfe8';
    drawRoundedRect(ctx, 60, 140, 600, photoBottom - 180, 32);
    ctx.fill();
  }

  // 手型标题
  ctx.fillStyle = '#1f2937';
  ctx.font = '700 64px "Plus Jakarta Sans", "Microsoft YaHei", sans-serif';
  ctx.fillText(handTypeTitle.textContent || '未知手型', 60, 520);

  // 人格标签
  ctx.font = '500 32px "Plus Jakarta Sans", "Microsoft YaHei", sans-serif';
  ctx.fillStyle = '#be185d';
  const tags = [tag1.textContent, tag2.textContent, tag3.textContent].filter(Boolean);
  let tagX = 60;
  tags.forEach((tag) => {
    const width = ctx.measureText(tag).width + 44;
    ctx.fillStyle = '#fce7f3';
    drawRoundedRect(ctx, tagX, 556, width, 56, 28);
    ctx.fill();
    ctx.fillStyle = '#be185d';
    ctx.fillText(tag, tagX + 22, 594);
    tagX += width + 20;
  });

  // 免费解读摘要
  ctx.fillStyle = '#374151';
  ctx.font = '400 30px "Plus Jakarta Sans", "Microsoft YaHei", sans-serif';
  const summary = (overview[0] && overview[0].shortInterpretation) || '掌纹里藏着你的下一步线索。';
  const endY = wrapText(ctx, summary, 60, 680, 600, 46);

  // 稀有印记
  if (rareMarkName.textContent && rareMarkName.textContent !== '-') {
    ctx.fillStyle = '#9333ea';
    ctx.font = '600 36px "Plus Jakarta Sans", "Microsoft YaHei", sans-serif';
    ctx.fillText('稀有印记 · ' + rareMarkName.textContent, 60, Math.max(endY + 60, 780));
  }

  // 底部品牌与水印
  ctx.fillStyle = '#9ca3af';
  ctx.font = '400 26px "Plus Jakarta Sans", "Microsoft YaHei", sans-serif';
  ctx.fillText('你的互联网手相搭子，科学聊玄学', 60, h - 110);
  ctx.font = '400 22px "Plus Jakarta Sans", "Microsoft YaHei", sans-serif';
  ctx.fillText('解读由 AI 生成，仅供娱乐', 60, h - 64);

  shareCardDownload.href = shareCardCanvas.toDataURL('image/png');
};

shareCardButton.addEventListener('click', () => {
  drawShareCard();
  shareCardModal.classList.remove('hidden');
  if (sessionId) {
    track('share_card', sessionId, 'report_share');
  }
});

shareCardClose.addEventListener('click', () => shareCardModal.classList.add('hidden'));
shareCardModal.addEventListener('click', (event) => {
  if (event.target === shareCardModal) {
    shareCardModal.classList.add('hidden');
  }
});

loadReport();
