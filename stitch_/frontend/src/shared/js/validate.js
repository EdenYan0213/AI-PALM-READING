// 手掌照片本地校验 + 压缩（原先 light_2 与 weekly_quick 各一份且已分叉，
// 周记录那份是 64px 宽松版——统一收敛到 72px 严格版）。

const isSkinTone = (r, g, b) => {
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  return r > 95 && g > 40 && b > 20 && (max - min) > 15 && Math.abs(r - g) > 15 && r > g && r > b;
};

/**
 * 客户端压缩：长边压到 maxSide 以内（视觉模型足够用），体积降 80%+，
 * 避免 base64 大图撑爆 localStorage 配额与上传带宽。解码失败原样返回。
 */
export function compressDataUrl(dataUrl, maxSide = 1280, quality = 0.82) {
  return new Promise((resolve) => {
    const image = new Image();
    image.onload = () => {
      const scale = Math.min(1, maxSide / Math.max(image.naturalWidth, image.naturalHeight));
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.round(image.naturalWidth * scale));
      canvas.height = Math.max(1, Math.round(image.naturalHeight * scale));
      const ctx = canvas.getContext('2d');
      if (!ctx) {
        resolve(dataUrl);
        return;
      }
      ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
      try {
        resolve(canvas.toDataURL('image/jpeg', quality));
      } catch (error) {
        resolve(dataUrl);
      }
    };
    image.onerror = () => resolve(dataUrl);
    image.src = dataUrl;
  });
}

/**
 * 皮肤色启发式手掌校验（拍照/上传时的第一道闸门，服务端还有 AI 校验兜底）：
 * 72px 缩略图上要求足够肤色占比 + 最大连通域成规模 + 中心区域有肤色聚集。
 */
export function validatePalmImage(dataUrl) {
  return new Promise((resolve) => {
    const image = new Image();
    image.onload = () => {
      const size = 72;
      const canvas = document.createElement('canvas');
      canvas.width = size;
      canvas.height = size;
      const ctx = canvas.getContext('2d', { willReadFrequently: true });
      ctx.drawImage(image, 0, 0, size, size);
      const data = ctx.getImageData(0, 0, size, size).data;

      const mask = new Uint8Array(size * size);
      let skinCount = 0;
      for (let i = 0; i < size * size; i++) {
        const offset = i * 4;
        if (isSkinTone(data[offset], data[offset + 1], data[offset + 2])) {
          mask[i] = 1;
          skinCount += 1;
        }
      }

      const totalCells = size * size;
      const minSkinCount = Math.round(totalCells * 0.11);
      if (skinCount < minSkinCount) {
        resolve(false);
        return;
      }

      const visited = new Uint8Array(totalCells);
      let largestComponent = 0;
      const queue = [];
      const neighbors = [
        [-1, 0],
        [1, 0],
        [0, -1],
        [0, 1]
      ];

      for (let index = 0; index < totalCells; index++) {
        if (!mask[index] || visited[index]) {
          continue;
        }
        let componentSize = 0;
        queue.length = 0;
        queue.push(index);
        visited[index] = 1;
        while (queue.length > 0) {
          const current = queue.pop();
          componentSize += 1;
          const x = current % size;
          const y = Math.floor(current / size);
          for (const [dx, dy] of neighbors) {
            const nx = x + dx;
            const ny = y + dy;
            if (nx < 0 || ny < 0 || nx >= size || ny >= size) {
              continue;
            }
            const nextIndex = ny * size + nx;
            if (mask[nextIndex] && !visited[nextIndex]) {
              visited[nextIndex] = 1;
              queue.push(nextIndex);
            }
          }
        }
        largestComponent = Math.max(largestComponent, componentSize);
      }

      const centerStart = Math.floor(size * 0.25);
      const centerEnd = Math.floor(size * 0.75);
      let centerSkin = 0;
      for (let y = centerStart; y < centerEnd; y++) {
        for (let x = centerStart; x < centerEnd; x++) {
          if (mask[y * size + x]) {
            centerSkin += 1;
          }
        }
      }

      const centerThreshold = Math.round((centerEnd - centerStart) * (centerEnd - centerStart) * 0.14);
      resolve(largestComponent >= Math.max(18, Math.round(skinCount * 0.45)) && centerSkin >= centerThreshold);
    };
    image.onerror = () => resolve(false);
    image.src = dataUrl;
  });
}
