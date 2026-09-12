// 全站唯一的 API 客户端：统一 BASE、超时、错误归一化、SSE 解析、身份与埋点。
// 原先 9 个页面各复制一份 API_BASE（含 file:// 分支）与 ensureUserId。

import { store } from './store.js';

const API_BASE = window.location.origin + '/api/v1';

/**
 * 归一化的请求封装：非 2xx 时抛出带 code/status 的 Error（message 取后端 payload）。
 * 后端错误契约：{ code, message }（见 ApiExceptionHandler）。
 */
export async function request(path, { method = 'GET', body, timeoutMs = 30000, headers = {} } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(API_BASE + path, {
      method,
      headers: body !== undefined ? { 'Content-Type': 'application/json', ...headers } : headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal
    });
    if (!response.ok) {
      let code = 'HTTP_' + response.status;
      let message = '服务暂时不可用，请稍后再试';
      try {
        const payload = await response.json();
        if (payload && payload.code) {
          code = payload.code;
        }
        if (payload && payload.message) {
          message = payload.message;
        }
      } catch (parseError) {
        // 非 JSON 错误体，保留默认文案
      }
      const error = new Error(message);
      error.code = code;
      error.status = response.status;
      throw error;
    }
    return response.json();
  } finally {
    clearTimeout(timer);
  }
}

export const get = (path, options) => request(path, options);
export const post = (path, body, options) => request(path, { ...options, method: 'POST', body });

/**
 * SSE 流式 POST（/palm/analyze/stream）。
 * onEvent(eventName, payload)：stage/progress/done/error 事件逐个回调。
 * 服务端不支持流式（非 event-stream 响应）时 resolve 为 null，由调用方回退普通 POST。
 */
export async function postSse(path, body, onEvent, { timeoutMs = 150000 } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(API_BASE + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: controller.signal
    });
    const contentType = response.headers.get('Content-Type') || '';
    if (!response.ok || !contentType.includes('text/event-stream')) {
      return null;
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let donePayload = null;
    for (;;) {
      const chunk = await reader.read();
      if (chunk.done) {
        break;
      }
      buffer += decoder.decode(chunk.value, { stream: true });
      let boundary;
      while ((boundary = buffer.indexOf('\n\n')) >= 0) {
        const rawEvent = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        let eventName = 'message';
        let dataText = '';
        for (const line of rawEvent.split('\n')) {
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            dataText += line.slice(5).trim();
          }
        }
        if (!dataText) {
          continue;
        }
        const payload = JSON.parse(dataText);
        if (eventName === 'done') {
          donePayload = payload;
        }
        onEvent(eventName, payload);
      }
    }
    return donePayload;
  } finally {
    clearTimeout(timer);
  }
}

/** 确保本地身份：优先复用已签发的 userId，向后端换取/续签。 */
export async function ensureUserId() {
  let uid = store.getUserId() || '';
  try {
    const identity = await get('/user/identity' + (uid ? '?previous=' + encodeURIComponent(uid) : ''));
    if (identity && identity.userId) {
      uid = identity.userId;
      store.setUserId(uid);
    }
  } catch (error) {
    // 离线时沿用本地身份
  }
  return uid;
}

/** 埋点：失败静默（指标事件可容忍）。 */
export function track(eventName, sessionId, channel) {
  post('/events/track', { eventName, sessionId, channel }).catch(() => {});
}
