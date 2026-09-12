import { get } from '/src/shared/js/api.js';

const TOKEN_KEY = 'palmistry.adminToken';
const refreshBtn = document.getElementById('refreshBtn');
const statusText = document.getElementById('statusText');

const $ = (id) => document.getElementById(id);

const clampRate = (num) => {
  if (Number.isNaN(num)) return 0;
  return Math.max(0, Math.min(100, num));
};

const bindRate = (value, textId, barId) => {
  const rate = clampRate(Number(value || 0));
  $(textId).textContent = rate.toFixed(2) + '%';
  $(barId).style.width = rate + '%';
};

const render = (data) => {
  $('kpiSingle').textContent = data.totalSingleAnalyze ?? 0;
  $('kpiCp').textContent = data.totalCpAnalyze ?? 0;
  $('kpiAd').textContent = data.totalAdUnlock ?? 0;
  $('kpiWeekly').textContent = data.totalWeeklyRecord ?? 0;
  $('kpiRare').textContent = data.totalRareMarkQuery ?? 0;

  bindRate(data.adUnlockRate, 'rateAdText', 'rateAdBar');
  bindRate(data.cpInitiateRate, 'rateCpText', 'rateCpBar');
  bindRate(data.shareRate, 'rateShareText', 'rateShareBar');
  bindRate(data.privateLeadRate, 'rateLeadText', 'rateLeadBar');
};

const load = async () => {
  statusText.textContent = '正在拉取指标数据...';
  statusText.className = 'status';

  const headers = {};
  const token = sessionStorage.getItem(TOKEN_KEY);
  if (token) {
    headers['X-Admin-Token'] = token;
  }

  try {
    const data = await get('/metrics/summary', { headers });
    render(data);
    statusText.textContent = '已连接后端，数据刷新成功';
    statusText.className = 'status ok';
  } catch (error) {
    if (error.status === 401 || error.status === 403) {
      const tokenInput = window.prompt('该看板受访问令牌保护，请输入 app.admin.token 对应的令牌：');
      if (tokenInput) {
        sessionStorage.setItem(TOKEN_KEY, tokenInput);
        await load();
        return;
      }
      statusText.textContent = '未提供访问令牌，无法读取指标';
      statusText.className = 'status err';
      return;
    }
    statusText.textContent = '连接失败：请确认 Spring Boot 已启动（dev/prod 任一环境）';
    statusText.className = 'status err';
  }
};

refreshBtn.addEventListener('click', load);
load();
setInterval(load, 15000);
