// 分享卡公共绘制工具（原先 light_3 与 monthly_energy 各实现一份文字折行）。

/** 逐字折行绘制中文文本，返回结束 y 坐标。超出行数即停。 */
export function wrapText(ctx, text, x, y, maxWidth, lineHeight, maxLines = Infinity) {
  let line = '';
  let cursorY = y;
  let lines = 0;
  for (let i = 0; i < (text || '').length; i++) {
    const test = line + text[i];
    if (ctx.measureText(test).width > maxWidth) {
      ctx.fillText(line, x, cursorY);
      line = text[i];
      cursorY += lineHeight;
      lines += 1;
      if (cursorY + lineHeight > y + maxLines * lineHeight) {
        return cursorY;
      }
    } else {
      line = test;
    }
  }
  if (line) {
    ctx.fillText(line, x, cursorY);
  }
  return cursorY;
}

/** 链接分享埋点：失败静默。 */
export async function trackShare(trackFn, sessionId, channel) {
  try {
    await trackFn('share_card', sessionId, channel);
  } catch (error) {
    // 忽略埋点失败
  }
}
