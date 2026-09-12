package com.palmistrylab.api.common;

public final class TextUtils {

  private TextUtils() {
  }

  /** 压缩空白并截断到 maxLength。 */
  public static String normalizeText(String text, int maxLength) {
    if (text == null) {
      return "";
    }
    String normalized = text.replaceAll("\\s+", " ").trim();
    if (normalized.length() > maxLength) {
      return normalized.substring(0, maxLength);
    }
    return normalized;
  }

  /** 记录模式归一化：仅接受白名单值。 */
  public static String normalizeMode(String mode, String fallback) {
    if (mode == null || mode.isBlank()) {
      return fallback;
    }
    String normalized = mode.trim().toLowerCase();
    if ("quick".equals(normalized) || "full".equals(normalized) || "standard".equals(normalized)
        || "single".equals(normalized)) {
      return normalized;
    }
    return fallback;
  }
}
