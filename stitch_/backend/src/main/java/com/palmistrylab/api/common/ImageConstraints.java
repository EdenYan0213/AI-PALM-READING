package com.palmistrylab.api.common;

/** 服务端图片体积护栏：客户端压缩失效或被绕过时的兜底。 */
public final class ImageConstraints {

  /** base64 字符数上限（约 9MB 原图）。 */
  public static final int MAX_IMAGE_CHARS = 12_000_000;

  private ImageConstraints() {
  }

  public static void requireReasonableImage(String imageData) {
    if (imageData != null && imageData.length() > MAX_IMAGE_CHARS) {
      throw new IllegalArgumentException("图片过大，请压缩后重新上传");
    }
  }
}
