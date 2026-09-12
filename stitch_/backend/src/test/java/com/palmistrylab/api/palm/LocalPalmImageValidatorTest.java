package com.palmistrylab.api.palm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalPalmImageValidatorTest {

  private final LocalPalmImageValidator validator = new LocalPalmImageValidator(new PalmRules());

  @Test
  void rejectsNonPalmFixture() throws Exception {
    byte[] bytes = java.nio.file.Files.readAllBytes(
        java.nio.file.Path.of("src/test/resources/fixtures/not-a-palm.png"));
    String dataUrl = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(bytes);

    assertThat(validator.looksLikePalm(dataUrl)).isFalse();
  }

  @Test
  void rejectsInvalidImageData() {
    assertThat(validator.looksLikePalm(null)).isFalse();
    assertThat(validator.looksLikePalm("")).isFalse();
    assertThat(validator.looksLikePalm("data:image/png;base64,@@not-base64@@")).isFalse();
  }

  @Test
  void decodeImageHandlesBareBase64WithoutDataUrlPrefix() {
    // 解码失败返回 null 而不抛异常
    assertThat(LocalPalmImageValidator.decodeImage("not-a-valid-image")).isNull();
  }

  @Test
  void clampConfidenceBounds() {
    assertThat(LocalPalmImageValidator.clampConfidence(1.5)).isEqualTo(1.0);
    assertThat(LocalPalmImageValidator.clampConfidence(-0.5)).isEqualTo(0.0);
    assertThat(LocalPalmImageValidator.clampConfidence(Double.NaN)).isEqualTo(0.0);
    assertThat(LocalPalmImageValidator.clampConfidence(0.7)).isEqualTo(0.7);
  }
}
