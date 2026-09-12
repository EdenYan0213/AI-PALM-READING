import '/src/styles/main.css';
import { ensureUserId, get } from '/src/shared/js/api.js';

const calendarGrid = document.getElementById('calendarGrid');
const monthTitle = document.getElementById('monthTitle');
const monthCountText = document.getElementById('monthCountText');
const trendSvg = document.getElementById('trendSvg');
const unlockHint = document.getElementById('unlockHint');
const monthlyReportButton = document.getElementById('monthlyReportButton');

let userId = null;
let monthCursor = new Date();

const toYM = (d) => {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  return y + '-' + m;
};

const runeClass = (color) => {
  if (color === 'gold') return 'bg-amber-400';
  if (color === 'purple') return 'bg-purple-400';
  return 'bg-blue-400';
};

const drawTrend = (trend) => {
  trendSvg.innerHTML = '';
  if (!Array.isArray(trend) || trend.length === 0) {
    trendSvg.innerHTML = '<text x="20" y="64" fill="#64748b" font-size="12">本月暂无趋势数据，完成记录后这里会亮起来。</text>';
    return;
  }

  const points = trend.map((item, index) => {
    const x = 24 + (index * (272 / Math.max(1, trend.length - 1)));
    const y = 104 - (Math.max(1, Math.min(10, item.energyLevel)) - 1) * 10;
    return { x, y, date: item.date };
  });

  const path = points.map((p, i) => (i === 0 ? 'M' : 'L') + p.x.toFixed(1) + ',' + p.y.toFixed(1)).join(' ');
  trendSvg.innerHTML += '<path d="' + path + '" fill="none" stroke="#ec4899" stroke-width="2.5" />';
  points.forEach((p) => {
    trendSvg.innerHTML += '<circle cx="' + p.x + '" cy="' + p.y + '" r="3" fill="#ec4899"></circle>';
  });
};

const renderCalendar = (monthKey, records) => {
  calendarGrid.innerHTML = '';
  const [year, month] = monthKey.split('-').map(Number);
  const firstDay = new Date(year, month - 1, 1);
  const days = new Date(year, month, 0).getDate();
  const weekdayOffset = (firstDay.getDay() + 6) % 7;

  const map = {};
  (records || []).forEach((record) => {
    map[record.date] = record;
  });

  for (let i = 0; i < weekdayOffset; i++) {
    const empty = document.createElement('div');
    empty.className = 'day-cell';
    calendarGrid.appendChild(empty);
  }

  for (let day = 1; day <= days; day++) {
    const date = monthKey + '-' + String(day).padStart(2, '0');
    const record = map[date];
    const cell = document.createElement('button');
    cell.className = 'day-cell rounded-xl border border-white/80 bg-white/70 p-2 text-left';
    cell.innerHTML = '<div class="text-xs text-slate-500">' + day + '</div>';
    if (record) {
      cell.innerHTML += '<div class="mt-2 flex items-center gap-1"><i class="w-2 h-2 rounded-full ' + runeClass(record.runeColor) + '"></i><span class="text-[10px] text-slate-600">已记录</span></div>';
      cell.addEventListener('click', () => {
        // 日期经 URL 传递即可，身份统一取本地签发结果
        window.location.href = '/record-detail.html?date=' + encodeURIComponent(date);
      });
    } else {
      cell.addEventListener('click', () => {
        const today = new Date().toISOString().slice(0, 10);
        if (date === today) {
          window.location.href = '/weekly.html';
        }
      });
    }
    calendarGrid.appendChild(cell);
  }
};

const load = async () => {
  userId = await ensureUserId();
  const ym = toYM(monthCursor);
  monthTitle.textContent = ym;
  const payload = await get('/record/calendar?userId=' + encodeURIComponent(userId) + '&yearMonth=' + encodeURIComponent(ym));
  monthCountText.textContent = '本月已记录 ' + (payload.monthRecordCount || 0) + '/' + (payload.monthTarget || 4) + ' 次';
  const unlocked = (payload.monthRecordCount || 0) >= 4;
  unlockHint.textContent = unlocked ? '已满足4次记录，月度月相图已解锁' : '累计4次可解锁月度月相图';
  monthlyReportButton.href = '/monthly.html?yearMonth=' + encodeURIComponent(payload.yearMonth || ym);
  monthlyReportButton.textContent = unlocked ? '查看月度能量月相图' : '月度能量月相图（未解锁）';
  monthlyReportButton.className = unlocked
    ? 'mt-3 block text-center py-2 rounded-full bg-indigo-600 text-white'
    : 'mt-3 block text-center py-2 rounded-full bg-indigo-500 text-white opacity-50 pointer-events-none';
  renderCalendar(payload.yearMonth, payload.records || []);
  drawTrend(payload.trend || []);
};

document.getElementById('prevMonthButton').addEventListener('click', async () => {
  monthCursor = new Date(monthCursor.getFullYear(), monthCursor.getMonth() - 1, 1);
  await load();
});
document.getElementById('nextMonthButton').addEventListener('click', async () => {
  monthCursor = new Date(monthCursor.getFullYear(), monthCursor.getMonth() + 1, 1);
  await load();
});

load().catch(() => {
  monthCountText.textContent = '加载失败，请确认后端服务已启动';
});
