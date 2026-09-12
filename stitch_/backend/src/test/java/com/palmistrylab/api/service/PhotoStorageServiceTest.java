package com.palmistrylab.api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhotoStorageServiceTest {

  @TempDir
  Path tempDir;

  @Test
  void savesDataUrlAndReturnsRelativePath() throws Exception {
    PhotoStorageService storage = new PhotoStorageService(tempDir.toString());
    byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
    String dataUrl = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(png);

    String stored = storage.saveDataUrl(dataUrl);

    assertThat(stored).isNotNull().endsWith(".png");
    Path resolved = storage.resolve(stored);
    assertThat(resolved).startsWith(tempDir);
    assertThat(Files.readAllBytes(resolved)).isEqualTo(png);
  }

  @Test
  void saveFailureReturnsNullForInvalidData() {
    PhotoStorageService storage = new PhotoStorageService(tempDir.toString());

    assertThat(storage.saveDataUrl(null)).isNull();
    assertThat(storage.saveDataUrl("")).isNull();
    assertThat(storage.saveDataUrl("data:image/png;base64,@@not-base64@@")).isNull();
    assertThat(storage.saveDataUrl("data:image/png;base64,")).isNull();
  }

  @Test
  void resolveRejectsPathTraversal() {
    PhotoStorageService storage = new PhotoStorageService(tempDir.toString());

    assertThatThrownBy(() -> storage.resolve("../outside.jpg"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
