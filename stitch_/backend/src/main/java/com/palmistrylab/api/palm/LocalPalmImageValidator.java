package com.palmistrylab.api.palm;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * 无感知边车/无 LLM 时的本地手掌校验启发式（TD §13.1 兜底层）：
 * 64x64 缩略图上做肤色像素占比 + 最大连通域 + 中心聚集判定。纯函数、无状态。
 */
public final class LocalPalmImageValidator {

  private LocalPalmImageValidator() {
  }

  public static boolean looksLikePalm(String imageData) {
    BufferedImage sourceImage = decodeImage(imageData);
    if (sourceImage == null) {
      return false;
    }

    int size = 64;
    BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = scaled.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics.drawImage(sourceImage, 0, 0, size, size, null);
    } finally {
      graphics.dispose();
    }

    int totalCells = size * size;
    byte[] mask = new byte[totalCells];
    int skinCount = 0;

    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        int rgb = scaled.getRGB(x, y);
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        if (isSkinTone(red, green, blue)) {
          mask[y * size + x] = 1;
          skinCount += 1;
        }
      }
    }

    if (skinCount < Math.round(totalCells * 0.12)) {
      return false;
    }

    boolean[] visited = new boolean[totalCells];
    int dominantComponent = 0;
    int dominantMinX = 0;
    int dominantMinY = 0;
    int dominantMaxX = 0;
    int dominantMaxY = 0;
    int largeComponents = 0;
    int[] queue = new int[totalCells];
    int[][] neighbors = new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    for (int index = 0; index < totalCells; index++) {
      if (mask[index] == 0 || visited[index]) {
        continue;
      }

      int componentSize = 0;
      int minX = size;
      int minY = size;
      int maxX = 0;
      int maxY = 0;
      int head = 0;
      int tail = 0;
      queue[tail++] = index;
      visited[index] = true;

      while (head < tail) {
        int current = queue[head++];
        componentSize += 1;
        int x = current % size;
        int y = current / size;
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);

        for (int[] neighbor : neighbors) {
          int nx = x + neighbor[0];
          int ny = y + neighbor[1];
          if (nx < 0 || ny < 0 || nx >= size || ny >= size) {
            continue;
          }
          int nextIndex = ny * size + nx;
          if (mask[nextIndex] == 1 && !visited[nextIndex]) {
            visited[nextIndex] = true;
            queue[tail++] = nextIndex;
          }
        }
      }

      if (componentSize > dominantComponent) {
        dominantComponent = componentSize;
        dominantMinX = minX;
        dominantMinY = minY;
        dominantMaxX = maxX;
        dominantMaxY = maxY;
      }
      if (componentSize >= Math.round(totalCells * 0.04)) {
        largeComponents += 1;
      }
    }

    if (dominantComponent <= 0) {
      return false;
    }

    double dominantRatio = dominantComponent / (double) totalCells;
    int dominantWidth = dominantMaxX - dominantMinX + 1;
    int dominantHeight = dominantMaxY - dominantMinY + 1;
    double dominantBoxRatio = (dominantWidth * dominantHeight) / (double) totalCells;
    double centerX = (dominantMinX + dominantMaxX) / 2.0 / size;
    double centerY = (dominantMinY + dominantMaxY) / 2.0 / size;

    int centerStart = (int) Math.floor(size * 0.25);
    int centerEnd = (int) Math.floor(size * 0.75);
    int centerSkin = 0;
    for (int y = centerStart; y < centerEnd; y++) {
      for (int x = centerStart; x < centerEnd; x++) {
        if (mask[y * size + x] == 1) {
          centerSkin += 1;
        }
      }
    }

    int centerThreshold = (int) Math.round((centerEnd - centerStart) * (centerEnd - centerStart) * 0.16);
    boolean centerAligned = centerX >= 0.22 && centerX <= 0.78 && centerY >= 0.22 && centerY <= 0.80;
    return dominantRatio >= 0.18
        && dominantRatio <= 0.82
        && dominantBoxRatio >= 0.24
        && centerAligned
        && centerSkin >= centerThreshold
        && largeComponents <= 2;
  }

  public static BufferedImage decodeImage(String imageData) {
    try {
      String payload = imageData.trim();
      int commaIndex = payload.indexOf(',');
      if (payload.startsWith("data:") && commaIndex >= 0) {
        payload = payload.substring(commaIndex + 1);
      }
      byte[] bytes = Base64.getDecoder().decode(payload);
      return ImageIO.read(new ByteArrayInputStream(bytes));
    } catch (Exception ignored) {
      return null;
    }
  }

  private static boolean isSkinTone(int red, int green, int blue) {
    int max = Math.max(red, Math.max(green, blue));
    int min = Math.min(red, Math.min(green, blue));
    return red > 95 && green > 40 && blue > 20 && (max - min) > 15 && Math.abs(red - green) > 15
        && red > green && red > blue;
  }

  public static double clampConfidence(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return 0.0;
    }
    return Math.max(0.0, Math.min(1.0, value));
  }
}
