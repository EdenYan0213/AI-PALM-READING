package com.palmistrylab.api.palm;

/** 图片未通过手掌校验：由异常处理器映射为 422 IMAGE_REJECTED，前端据此引导重新拍照。 */
public class ImageRejectedException extends RuntimeException {

  public ImageRejectedException(String message) {
    super(message);
  }
}
