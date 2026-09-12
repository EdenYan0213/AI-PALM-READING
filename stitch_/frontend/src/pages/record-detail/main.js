import '/src/styles/main.css';
import { ensureUserId, get } from '/src/shared/js/api.js';

const query = new URLSearchParams(window.location.search);
// 身份一律取本地签发的 userId（不再经 URL query 传递，避免外泄）。
const date = query.get('date') || new Date().toISOString().slice(0, 10);

const dateText = document.getElementById('dateText');
const energyText = document.getElementById('energyText');
const wisdomText = document.getElementById('wisdomText');
const emotionText = document.getElementById('emotionText');
const summaryText = document.getElementById('summaryText');
const noteText = document.getElementById('noteText');

dateText.textContent = '记录日期：' + date;

ensureUserId()
  .then((userId) => get('/record/detail?userId=' + encodeURIComponent(userId) + '&date=' + encodeURIComponent(date)))
  .then((payload) => {
    energyText.textContent = payload.energyLevel ?? '-';
    wisdomText.textContent = payload.wisdomActive ?? '-';
    emotionText.textContent = payload.emotionWave ?? '-';
    summaryText.textContent = payload.aiSummary || '暂无总结';
    noteText.textContent = payload.userNote || '未填写便签';
  })
  .catch(() => {
    summaryText.textContent = '未找到该日期记录或后端未启动';
    noteText.textContent = '无便签';
  });
