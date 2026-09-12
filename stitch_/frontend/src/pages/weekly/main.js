import '/src/styles/main.css';
import { ensureUserId, post } from '/src/shared/js/api.js';
import { store } from '/src/shared/js/store.js';
import { compressDataUrl, validatePalmImage } from '/src/shared/js/validate.js';

const photoInput = document.getElementById('photoInput');
const pickPhotoButton = document.getElementById('pickPhotoButton');
const previewWrap = document.getElementById('previewWrap');
const previewImage = document.getElementById('previewImage');
const submitButton = document.getElementById('submitButton');
const statusText = document.getElementById('statusText');
const resultCard = document.getElementById('resultCard');
const recordDateInput = document.getElementById('recordDateInput');

const compareHint = document.getElementById('compareHint');
const summaryText = document.getElementById('summaryText');
const energyValue = document.getElementById('energyValue');
const wisdomValue = document.getElementById('wisdomValue');
const emotionValue = document.getElementById('emotionValue');
const runeTag = document.getElementById('runeTag');
const noteInput = document.getElementById('noteInput');
const saveNoteButton = document.getElementById('saveNoteButton');
const noteStatusText = document.getElementById('noteStatusText');
const monthlyLink = document.getElementById('monthlyLink');

// 复用单人流程的拍照缓存（也支持本页自行上传）
let imageData = store.getCapturedImage() || '';
let currentRecordId = null;

const today = new Date();
const minDate = new Date(today.getTime() - 6 * 24 * 60 * 60 * 1000);
const toDateText = (d) => d.toISOString().slice(0, 10);
recordDateInput.max = toDateText(today);
recordDateInput.min = toDateText(minDate);
recordDateInput.value = toDateText(today);

if (imageData) {
  previewImage.src = imageData;
  previewWrap.classList.remove('hidden');
}

const renderResult = (payload) => {
  compareHint.textContent = payload.compareHint || '已生成本周对比';
  summaryText.textContent = payload.aiSummary || '';
  energyValue.textContent = payload.energyLevel ?? '-';
  wisdomValue.textContent = payload.wisdomActive ?? '-';
  emotionValue.textContent = payload.emotionWave ?? '-';

  const rune = payload.runeColor || 'blue';
  const map = {
    gold: ['金色符文', 'bg-amber-100 text-amber-700'],
    blue: ['蓝色符文', 'bg-blue-100 text-blue-700'],
    purple: ['紫色符文', 'bg-purple-100 text-purple-700']
  };
  const style = map[rune] || map.blue;
  runeTag.textContent = style[0];
  runeTag.className = 'text-xs px-2 py-1 rounded-full ' + style[1];
  currentRecordId = payload.recordId || null;
  noteInput.value = payload.userNote || '';
  monthlyLink.classList.toggle('hidden', !(payload.monthlyUnlocked === true));

  resultCard.classList.remove('hidden');
};

pickPhotoButton.addEventListener('click', () => photoInput.click());
photoInput.addEventListener('change', () => {
  const file = photoInput.files && photoInput.files[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = () => {
    // 相册原图先压缩再校验/暂存，避免 base64 大图撑爆 localStorage 配额
    compressDataUrl(String(reader.result || ''), 1280, 0.82).then((dataUrl) => {
      validatePalmImage(dataUrl).then((valid) => {
        if (!valid) {
          imageData = '';
          previewWrap.classList.add('hidden');
          statusText.textContent = '仅支持手掌照片，请上传清晰的手掌正面图片';
          return;
        }
        imageData = dataUrl;
        store.setCapturedImage(imageData);
        previewImage.src = imageData;
        previewWrap.classList.remove('hidden');
        statusText.textContent = '照片已通过手掌校验，可以开始生成报告';
      });
    });
  };
  reader.readAsDataURL(file);
});

submitButton.addEventListener('click', async () => {
  if (!imageData) {
    statusText.textContent = '请先上传手掌照片';
    return;
  }

  const valid = await validatePalmImage(imageData);
  if (!valid) {
    statusText.textContent = '仅支持手掌照片，请重新上传';
    return;
  }

  submitButton.disabled = true;
  statusText.textContent = '正在写入本周能量档案...';

  try {
    const payload = await post('/record/weekly', {
      userId: await ensureUserId(),
      recordMode: 'quick',
      imageData,
      recordDate: recordDateInput.value
    });
    store.setWeeklyRecord(payload);
    statusText.textContent = '你的本周能量已存入赛博手相档案';
    renderResult(payload);
  } catch (error) {
    statusText.textContent = '记录失败，请稍后重试';
  } finally {
    submitButton.disabled = false;
  }
});

saveNoteButton.addEventListener('click', async () => {
  if (!currentRecordId) {
    noteStatusText.textContent = '请先完成一次记录';
    return;
  }
  saveNoteButton.disabled = true;
  noteStatusText.textContent = '保存中...';
  try {
    await post('/record/note', {
      recordId: String(currentRecordId),
      userId: await ensureUserId(),
      userNote: String(noteInput.value || '').trim() || ' '
    });
    noteStatusText.textContent = '便签已保存';
  } catch (error) {
    noteStatusText.textContent = '保存失败，请稍后重试';
  } finally {
    saveNoteButton.disabled = false;
  }
});

try {
  const cache = store.getWeeklyRecord();
  if (cache) {
    renderResult(cache);
  }
} catch (e) {
  // Ignore cache parse errors.
}
