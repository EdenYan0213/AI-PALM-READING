import '/src/styles/main.css';
import { post, track } from '/src/shared/js/api.js';
import { store } from '/src/shared/js/store.js';

const shareCpButton = document.getElementById('shareCpButton');
const unlockCpButton = document.getElementById('unlockCpButton');
const cpUnlockModal = document.getElementById('cpUnlockModal');
const closeCpUnlock = document.getElementById('closeCpUnlock');
const confirmCpUnlock = document.getElementById('confirmCpUnlock');
const cpUnlockStatus = document.getElementById('cpUnlockStatus');
const cpScore = document.getElementById('cpScore');
const comboName = document.getElementById('comboName');
const dim1Bar = document.getElementById('dim1Bar');
const dim1Score = document.getElementById('dim1Score');
const dim2Bar = document.getElementById('dim2Bar');
const dim2Score = document.getElementById('dim2Score');
const cpInterpretation = document.getElementById('cpInterpretation');
const cpTip = document.getElementById('cpTip');
const cpDeepSectionContainer = document.getElementById('cpDeepSectionContainer');

let cpSessionId = null;

const renderCpPayload = (payload) => {
  cpSessionId = payload.cpSessionId || cpSessionId;
  cpScore.textContent = Number(payload.matchScore).toFixed(2) + '%';
  comboName.textContent = payload.comboName;
  cpInterpretation.textContent = payload.interpretation;
  cpTip.textContent = payload.tip;

  if (Array.isArray(payload.dimensions) && payload.dimensions.length >= 2) {
    dim1Score.textContent = payload.dimensions[0].score + '%';
    dim1Bar.style.width = payload.dimensions[0].score + '%';
    dim2Score.textContent = payload.dimensions[1].score + '%';
    dim2Bar.style.width = payload.dimensions[1].score + '%';
  }
};

const loadCpAnalyze = async () => {
  try {
    const payload = await post('/cp/analyze', {
      userA: {
        nickname: '你',
        handType: '水型手',
        mbti: 'INFJ'
      },
      userB: {
        nickname: 'TA',
        handType: '火型手',
        mbti: 'ENFP'
      }
    });
    store.setCpReport(payload);
    renderCpPayload(payload);
  } catch (error) {
    const cached = store.getCpReport();
    if (cached) {
      renderCpPayload(cached);
    }
  }
};

const renderCpDeepSections = (sections) => {
  cpDeepSectionContainer.innerHTML = '';
  if (!Array.isArray(sections) || sections.length === 0) {
    return;
  }

  sections.forEach((section) => {
    const card = document.createElement('article');
    card.className = 'glass-card p-6 rounded-3xl';
    card.innerHTML =
      '<h4 class="font-headline-md text-primary mb-3">' + section.title + '</h4>' +
      '<p class="text-on-surface-variant leading-relaxed">' + section.detail + '</p>' +
      '<p class="text-pink-600 text-sm mt-3">相处建议：' + section.cyberTip + '</p>';
    cpDeepSectionContainer.appendChild(card);
  });
};

const showModal = () => {
  cpUnlockModal.classList.remove('hidden');
  cpUnlockModal.classList.add('flex');
};

const hideModal = () => {
  cpUnlockModal.classList.remove('flex');
  cpUnlockModal.classList.add('hidden');
};

shareCpButton.addEventListener('click', async () => {
  const shareText = '我们在手相研究所的CP匹配度是' + cpScore.textContent + '，你也来测测看';
  track('share_card', cpSessionId, 'cp_page');
  try {
    if (navigator.share) {
      await navigator.share({
        title: '手相研究所 CP 宿命卡片',
        text: shareText,
        url: window.location.href
      });
      return;
    }
  } catch (error) {
    // 用户取消或环境不支持时回退 alert
  }

  window.alert(shareText);
});

unlockCpButton.addEventListener('click', showModal);
closeCpUnlock.addEventListener('click', hideModal);

confirmCpUnlock.addEventListener('click', async () => {
  confirmCpUnlock.disabled = true;
  confirmCpUnlock.textContent = '播放中...';
  cpUnlockStatus.textContent = '广告播放中，请稍候';

  try {
    await new Promise((resolve) => setTimeout(resolve, 1600));

    if (!cpSessionId) {
      throw new Error('missing cp session');
    }

    const payload = await post('/cp/unlock-deep', { sessionId: cpSessionId });
    renderCpDeepSections(payload.sections);
    unlockCpButton.textContent = '完整 CP 报告已解锁';
    unlockCpButton.disabled = true;
    unlockCpButton.classList.add('opacity-70', 'cursor-not-allowed');
    cpUnlockStatus.textContent = '解锁成功，已开放摩擦点与关系曲线';
    setTimeout(hideModal, 500);
  } catch (error) {
    cpUnlockStatus.textContent = '解锁失败，请确认后端服务已启动';
  } finally {
    confirmCpUnlock.disabled = false;
    confirmCpUnlock.textContent = '开始观看';
  }
});

loadCpAnalyze();
