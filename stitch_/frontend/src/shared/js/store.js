// 全站唯一的本地状态入口（原先 9 个 key 散布在 10 个页面，写/读/清三权分立不清）。
// 约定：
// - 只允许通过这里的命名方法读写，禁止页面直接拼 key；
// - 死键（tracePayload、capturedMeta）不再迁移；
// - 报告页渲染完成后调用 clearAfterReportViewed() 清理大体积拍照缓存。

const KEYS = {
  userId: 'palmistry.userId',
  privacyConsent: 'palmistry.privacyConsent',
  capturedImage: 'palmistry.capturedImage',
  singleReport: 'palmistry.singleReport',
  analyzeError: 'palmistry.analyzeError',
  weeklyRecord: 'palmistry.weeklyRecord',
  cpReport: 'palmistry.cpReport'
};

function read(key) {
  try {
    return localStorage.getItem(key);
  } catch (error) {
    return null;
  }
}

function write(key, value) {
  try {
    localStorage.setItem(key, value);
    return true;
  } catch (error) {
    // 配额满等写入失败：调用方自行决定降级策略
    return false;
  }
}

function remove(key) {
  try {
    localStorage.removeItem(key);
  } catch (error) {
    // 忽略
  }
}

export const store = {
  // 身份
  getUserId: () => read(KEYS.userId),
  setUserId: (value) => write(KEYS.userId, value),

  // 隐私同意
  hasPrivacyConsent: () => read(KEYS.privacyConsent) === 'yes',
  setPrivacyConsent: () => write(KEYS.privacyConsent, 'yes'),

  // 拍照流转（base64，体积大，用后即清）
  getCapturedImage: () => read(KEYS.capturedImage),
  setCapturedImage: (dataUrl) => write(KEYS.capturedImage, dataUrl),
  clearCapturedImage: () => remove(KEYS.capturedImage),

  // 单人报告
  getReport: () => {
    const raw = read(KEYS.singleReport);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw);
    } catch (error) {
      return null;
    }
  },
  setReport: (payload) => write(KEYS.singleReport, JSON.stringify(payload)),
  clearReport: () => remove(KEYS.singleReport),

  // 分析错误透传
  getAnalyzeError: () => read(KEYS.analyzeError),
  setAnalyzeError: (text) => write(KEYS.analyzeError, text),
  clearAnalyzeError: () => remove(KEYS.analyzeError),

  // 周记录
  getWeeklyRecord: () => {
    const raw = read(KEYS.weeklyRecord);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw);
    } catch (error) {
      return null;
    }
  },
  setWeeklyRecord: (payload) => write(KEYS.weeklyRecord, JSON.stringify(payload)),

  // CP 报告
  getCpReport: () => {
    const raw = read(KEYS.cpReport);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw);
    } catch (error) {
      return null;
    }
  },
  setCpReport: (payload) => write(KEYS.cpReport, JSON.stringify(payload))
};

/** 拍照/重新拍照前：清掉上一轮的分析产物，避免串状态。 */
export function clearAnalyzeArtifacts() {
  store.clearReport();
  store.clearAnalyzeError();
}

/** 报告页渲染完成后：拍照 base64 已完成使命，及时释放配额。 */
export function clearAfterReportViewed() {
  store.clearCapturedImage();
  store.clearAnalyzeError();
}
