import '/src/styles/main.css';
import { ensureUserId, get, post } from '/src/shared/js/api.js';
import { wrapText } from '/src/shared/js/shareCard.js';

const query = new URLSearchParams(window.location.search);
const yearMonth = query.get('yearMonth') || new Date().toISOString().slice(0, 7);

const subtitle = document.getElementById('subtitle');
const orbitSvg = document.getElementById('orbitSvg');
const dominantText = document.getElementById('dominantText');
const reportText = document.getElementById('reportText');
const shareCardButton = document.getElementById('shareCardButton');
const downloadCardLink = document.getElementById('downloadCardLink');
const shareStatusText = document.getElementById('shareStatusText');
const shareCanvas = document.getElementById('shareCanvas');

let latestPayload = null;

const renderOrbit = (trend, unlocked) => {
  orbitSvg.innerHTML = '';
  orbitSvg.innerHTML += '<defs><linearGradient id="ringGrad" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color="#7ea2ff"/><stop offset="100%" stop-color="#f472b6"/></linearGradient></defs>';
  orbitSvg.innerHTML += '<circle cx="180" cy="180" r="120" fill="none" stroke="url(#ringGrad)" stroke-width="3" opacity="0.8"/>';
  orbitSvg.innerHTML += '<circle cx="180" cy="180" r="80" fill="none" stroke="#4f628f" stroke-width="1.5" opacity="0.45"/>';

  if (!Array.isArray(trend) || trend.length === 0) {
    orbitSvg.innerHTML += '<text x="180" y="186" text-anchor="middle" fill="#adc2ff" font-size="13">暂无月度数据</text>';
    return;
  }

  const step = (Math.PI * 2) / Math.max(4, trend.length);
  trend.forEach((item, index) => {
    const angle = -Math.PI / 2 + (index * step);
    const energy = Math.max(1, Math.min(10, item.energyLevel || 1));
    const radius = 90 + (energy - 1) * 3;
    const x = 180 + Math.cos(angle) * radius;
    const y = 180 + Math.sin(angle) * radius;
    const color = energy >= 7 ? '#fbbf24' : (energy >= 4 ? '#60a5fa' : '#a78bfa');
    orbitSvg.innerHTML += '<circle cx="' + x.toFixed(1) + '" cy="' + y.toFixed(1) + '" r="5" fill="' + color + '" />';
    orbitSvg.innerHTML += '<text x="' + x.toFixed(1) + '" y="' + (y + 16).toFixed(1) + '" text-anchor="middle" fill="#bcd0ff" font-size="9">' + (item.date || '').slice(5) + '</text>';
  });

  const centerText = unlocked ? '月相已成环' : '记录未满4次';
  orbitSvg.innerHTML += '<text x="180" y="178" text-anchor="middle" fill="#edf2ff" font-size="16" font-weight="600">' + centerText + '</text>';
};

ensureUserId()
  .then((userId) => get('/record/monthly-report?userId=' + encodeURIComponent(userId) + '&yearMonth=' + encodeURIComponent(yearMonth)))
  .then((payload) => {
    latestPayload = payload;
    subtitle.textContent = payload.yearMonth + ' · 本月记录 ' + (payload.recordCount || 0) + '/4 次';
    dominantText.textContent = '本月主导能量：' + (payload.dominantEnergy || '休整之月');
    reportText.textContent = payload.reportText || '暂无月度总结';
    renderOrbit(payload.trend || [], payload.unlocked === true);
  })
  .catch(() => {
    subtitle.textContent = '读取失败，请确认后端服务已启动';
    dominantText.textContent = '本月主导能量：-';
    reportText.textContent = '暂无月度总结';
    renderOrbit([], false);
  });

const drawShareCard = (payload) => {
  const ctx = shareCanvas.getContext('2d');
  const w = shareCanvas.width;
  const h = shareCanvas.height;

  const bg = ctx.createLinearGradient(0, 0, 0, h);
  bg.addColorStop(0, '#111a38');
  bg.addColorStop(1, '#090f20');
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, w, h);

  const halo = ctx.createRadialGradient(180, 220, 20, 180, 220, 360);
  halo.addColorStop(0, 'rgba(168,141,255,0.45)');
  halo.addColorStop(1, 'rgba(168,141,255,0)');
  ctx.fillStyle = halo;
  ctx.fillRect(0, 0, w, h);

  ctx.fillStyle = '#d7e3ff';
  ctx.font = '700 46px "Microsoft YaHei"';
  ctx.fillText('我的能量月相图', 86, 126);

  ctx.fillStyle = '#a7b9ea';
  ctx.font = '500 30px "Microsoft YaHei"';
  ctx.fillText((payload.yearMonth || yearMonth) + ' · 赛博手相档案', 86, 176);

  const cx = w / 2;
  const cy = 620;
  ctx.strokeStyle = 'rgba(132,167,255,0.92)';
  ctx.lineWidth = 6;
  ctx.beginPath();
  ctx.arc(cx, cy, 260, 0, Math.PI * 2);
  ctx.stroke();

  const trend = Array.isArray(payload.trend) ? payload.trend : [];
  const count = Math.max(4, trend.length);
  ctx.textAlign = 'center';
  for (let i = 0; i < count; i++) {
    const angle = -Math.PI / 2 + (Math.PI * 2 * i / count);
    const t = trend[i] || { energyLevel: 1, date: '--' };
    const energy = Math.max(1, Math.min(10, Number(t.energyLevel || 1)));
    const radius = 180 + (energy - 1) * 8;
    const x = cx + Math.cos(angle) * radius;
    const y = cy + Math.sin(angle) * radius;
    const color = energy >= 7 ? '#fbbf24' : (energy >= 4 ? '#60a5fa' : '#a78bfa');

    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.arc(x, y, 14, 0, Math.PI * 2);
    ctx.fill();

    ctx.fillStyle = '#c8d7ff';
    ctx.font = '500 20px "Microsoft YaHei"';
    ctx.fillText(String((t.date || '--').slice(5)), x, y + 36);
  }

  ctx.fillStyle = '#eff4ff';
  ctx.font = '700 44px "Microsoft YaHei"';
  ctx.fillText(payload.dominantEnergy || '休整之月', cx, cy + 10);
  ctx.fillStyle = '#a9bbe8';
  ctx.font = '500 24px "Microsoft YaHei"';
  ctx.fillText('本月主导能量', cx, cy + 52);

  ctx.textAlign = 'left';
  ctx.fillStyle = '#d2defc';
  ctx.font = '500 26px "Microsoft YaHei"';
  wrapText(ctx, payload.reportText || '本月记录较少，建议持续每周打卡。', 86, 1020, w - 172, 40, 7);

  ctx.fillStyle = '#93a8db';
  ctx.font = '500 22px "Microsoft YaHei"';
  ctx.fillText('Palmistry AI · 你的互联网手相搭子', 86, 1370);
};

shareCardButton.addEventListener('click', async () => {
  if (!latestPayload) {
    shareStatusText.textContent = '月度数据未就绪，请稍后重试';
    return;
  }
  drawShareCard(latestPayload);
  const dataUrl = shareCanvas.toDataURL('image/png');
  downloadCardLink.href = dataUrl;
  downloadCardLink.classList.remove('hidden');
  shareStatusText.textContent = '分享卡片已生成，点击下载后可发朋友圈';

  try {
    await post('/events/track', {
      eventName: 'share_card',
      sessionId: 'MONTHLY-' + (latestPayload.yearMonth || yearMonth),
      channel: 'monthly_energy'
    });
  } catch (error) {
    // 埋点失败忽略
  }
});
