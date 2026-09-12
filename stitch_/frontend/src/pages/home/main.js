import '/src/styles/main.css';

// 修复原设计稿写死的“2024年5月20日 · 谷雨”：展示真实当天日期。
const today = new Date();
const todayText = document.getElementById('todayText');
if (todayText) {
  todayText.textContent = today.getFullYear() + '年' + (today.getMonth() + 1) + '月' + today.getDate() + '日';
}
