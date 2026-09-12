import '/src/styles/main.css';
import { compressDataUrl, validatePalmImage } from '/src/shared/js/validate.js';
import { store, clearAnalyzeArtifacts } from '/src/shared/js/store.js';

const photoInput = document.getElementById('photoInput');
const pickAlbumButton = document.getElementById('pickAlbumButton');
const startAnalyzeButton = document.getElementById('startAnalyzeButton');
const cameraPreview = document.getElementById('cameraPreview');
const photoPreviewWrap = document.getElementById('photoPreviewWrap');
const photoPreview = document.getElementById('photoPreview');
const cameraStatus = document.getElementById('cameraStatus');
const closeCameraButton = document.querySelector('button[data-icon="cached"]')?.closest('button');
let cameraStream = null;

const ensurePrivacyConsent = () => {
  if (store.hasPrivacyConsent()) {
    return true;
  }
  const agreed = window.confirm(
    '上传前请知悉：\n\n' +
    '1. 你上传的手掌照片将用于本次 AI 手相分析；\n' +
    '2. 内容由 AI 生成，仅供娱乐，不构成任何建议；\n' +
    '3. 请上传自己的手掌，不要包含他人肖像或其他敏感信息。\n\n' +
    '点击「确定」即表示你已阅读并同意以上说明。'
  );
  if (agreed) {
    store.setPrivacyConsent();
  } else {
    setStatus('需同意上述说明后才能继续，你也可以退出页面。', false);
  }
  return agreed;
};

const setStatus = (message, isError) => {
  cameraStatus.textContent = message;
  cameraStatus.className = isError
    ? 'relative z-10 mb-3 px-4 py-2 rounded-full bg-rose-50/85 backdrop-blur-md border border-rose-200 text-xs text-rose-700 shadow-sm'
    : 'relative z-10 mb-3 px-4 py-2 rounded-full bg-white/50 backdrop-blur-md border border-white/60 text-xs text-pink-700 shadow-sm';
};

const stopCamera = () => {
  if (cameraStream) {
    cameraStream.getTracks().forEach((track) => track.stop());
    cameraStream = null;
  }
  cameraPreview.srcObject = null;
  cameraPreview.classList.add('hidden');
};

const saveCapture = async (dataUrl, meta) => {
  // 先清上一轮分析产物释放配额，再保存；配额不足时逐级压缩重试。
  clearAnalyzeArtifacts();
  let stored = String(dataUrl);
  try {
    if (!store.setCapturedImage(stored)) {
      throw new Error('quota');
    }
  } catch (quotaError) {
    let saved = false;
    for (const [side, quality] of [[960, 0.7], [720, 0.6]]) {
      try {
        stored = await compressDataUrl(stored, side, quality);
        if (!store.setCapturedImage(stored)) {
          throw new Error('quota');
        }
        saved = true;
        break;
      } catch (retryError) {
        // 继续尝试更小尺寸
      }
    }
    if (!saved) {
      setStatus('照片过大，本地存储空间不足，请换一张照片重试。', true);
      return;
    }
  }
  photoPreview.src = stored;
  photoPreviewWrap.classList.remove('hidden');
  window.location.href = '/scanning.html?source=' + encodeURIComponent(meta.source || 'photo');
};

const openPicker = () => {
  stopCamera();
  photoInput.click();
};

const startCamera = async () => {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    setStatus('当前浏览器不支持直接打开摄像头，已切换到相册上传。', true);
    openPicker();
    return;
  }

  try {
    cameraStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
    cameraPreview.srcObject = cameraStream;
    cameraPreview.classList.remove('hidden');
    setStatus('摄像头已打开，再次点击中间按钮即可拍摄。');
  } catch (error) {
    setStatus('无法打开摄像头，请检查浏览器权限后改用相册上传。', true);
    openPicker();
  }
};

const captureFromCamera = async () => {
  if (!cameraStream || cameraPreview.videoWidth === 0 || cameraPreview.videoHeight === 0) {
    await startCamera();
    return;
  }

  const sourceW = cameraPreview.videoWidth;
  const sourceH = cameraPreview.videoHeight;
  const scale = Math.min(1, 1280 / Math.max(sourceW, sourceH));
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(sourceW * scale));
  canvas.height = Math.max(1, Math.round(sourceH * scale));
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    setStatus('摄像头暂时不可用，请改用相册上传。', true);
    return;
  }

  if (!ensurePrivacyConsent()) {
    return;
  }

  ctx.drawImage(cameraPreview, 0, 0, canvas.width, canvas.height);
  const dataUrl = canvas.toDataURL('image/jpeg', 0.82);
  const valid = await validatePalmImage(dataUrl);
  if (!valid) {
    setStatus('这张照片不像手掌，请重新拍摄一张手掌正面照片。', true);
    return;
  }

  setStatus('手掌识别通过，正在进入分析页...');
  stopCamera();
  await saveCapture(dataUrl, { name: 'camera-shot.jpg', type: 'image/jpeg', source: 'camera' });
};

pickAlbumButton.addEventListener('click', openPicker);
startAnalyzeButton.addEventListener('click', captureFromCamera);
closeCameraButton?.addEventListener('click', () => {
  stopCamera();
  setStatus('摄像头已关闭，可以继续上传相册照片。');
});

photoInput.addEventListener('change', async () => {
  const file = photoInput.files && photoInput.files[0];
  if (!file) {
    return;
  }

  const reader = new FileReader();
  reader.onload = () => {
    if (!ensurePrivacyConsent()) {
      return;
    }
    // 相册原图可能好几 MB：先压缩再校验/保存。
    compressDataUrl(String(reader.result), 1280, 0.82).then((dataUrl) => {
      validatePalmImage(dataUrl).then((valid) => {
        if (!valid) {
          setStatus('这张图片不像手掌，请上传手掌正面照片。', true);
          store.clearCapturedImage();
          return;
        }
        setStatus('手掌识别通过，正在进入分析页...');
        saveCapture(dataUrl, { name: file.name, type: file.type, size: file.size, source: 'album' });
      });
    });
  };
  reader.readAsDataURL(file);
});
