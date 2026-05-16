export interface CameraStatus {
  available: boolean;
  stream: MediaStream | null;
  error: string | null;
}

export interface PalmLinePoints {
  lifeLine: [number, number][];
  wisdomLine: [number, number][];
  loveLine: [number, number][];
}

export interface TracesData {
  lifeLine: [number, number][];
  wisdomLine: [number, number][];
  loveLine: [number, number][];
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

export interface AnalyzeRequest {
  imageData: string;
  gender?: string;
  age?: number;
}

export interface Stage {
  id: string;
  label: string;
}
