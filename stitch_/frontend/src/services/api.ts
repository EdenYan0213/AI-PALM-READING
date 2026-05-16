const API_BASE = '/api/v1';

async function request<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const url = `${API_BASE}${path}`;
  const res = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
    ...options,
  });

  if (!res.ok) {
    let errMsg = `HTTP ${res.status}`;
    try {
      const errBody = await res.json();
      errMsg = errBody.message || errBody.msg || errMsg;
    } catch {}
    throw new Error(errMsg);
  }

  return res.json();
}

export interface HealthResponse {
  status: string;
  name: string;
  version: string;
}

export async function healthCheck(): Promise<HealthResponse> {
  return request<HealthResponse>('/health');
}

export interface PalmLineSummary {
  lineName: string;
  tags: string;
  shortInterpretation: string;
  detailInterpretation?: string;
  actionableAdvice?: string;
  timeWindow?: string;
}

export interface AnalyzeResponse {
  sessionId: string;
  handType: string;
  personalityTags: string[];
  freeOverview: PalmLineSummary[];
  rareMark: string;
  teaser: string;
  slogan: string;
  traceConfirmed: boolean;
  traceFeatureSummary: string;
  llmUsed: boolean;
  llmStatus: string;
  overallSummary?: string;
  careerTip?: string;
  loveTip?: string;
  healthTip?: string;
  luckyPeriod?: string;
  llmEnhancedAvailable?: boolean;
}

export async function analyzePalm(
  imageData: string,
  gender?: string
): Promise<AnalyzeResponse> {
  return request<AnalyzeResponse>('/palm/analyze', {
    method: 'POST',
    body: JSON.stringify({
      source: 'camera',
      handSide: 'left',
      imageData,
      gender: gender ?? 'unknown',
    }),
  });
}

export interface UnlockDeepResponse {
  sessionId: string;
  unlocked: boolean;
  unlockType: string;
  sections: { title: string; detail: string; cyberTip: string }[];
}

export async function unlockDeep(sessionId: string): Promise<UnlockDeepResponse> {
  return request<UnlockDeepResponse>('/palm/unlock-deep', {
    method: 'POST',
    body: JSON.stringify({ sessionId }),
  });
}

export interface RareMarkQueryResponse {
  sessionId: string;
  markName: string;
  wechatId: string;
  remainQuota: number;
  hint: string;
}

export async function queryRareMark(sessionId: string): Promise<RareMarkQueryResponse> {
  return request<RareMarkQueryResponse>('/palm/rare-mark', {
    method: 'POST',
    body: JSON.stringify({ sessionId }),
  });
}

export interface CpAnalyzeResponse {
  cpSessionId: string;
  matchScore: number;
  comboName: string;
  dimensions: { name: string; score: number }[];
  interpretation: string;
  tip: string;
}

export async function analyzeCp(
  userA: { nickname: string; handType: string; mbti: string },
  userB: { nickname: string; handType: string; mbti: string }
): Promise<CpAnalyzeResponse> {
  return request<CpAnalyzeResponse>('/cp/analyze', {
    method: 'POST',
    body: JSON.stringify({ userA, userB }),
  });
}
