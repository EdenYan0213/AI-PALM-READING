package com.palmistrylab.api.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

/**
 * 照片落盘存储：photo_url 列只存相对路径（如 2026/09/xxx.jpg），
 * 不再把整张 base64 图片写进 MySQL TEXT（拖累写入、备份与缓冲池）。
 * 保存失败不抛出——照片是附属数据，记录本身必须成功。
 */
@Service
public class PhotoStorageService {

  private final Path baseDir;

  public PhotoStorageService(@Value("${app.photos.dir:./data/photos}") String baseDir) {
    this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
  }

  /**
   * 解码 data URL / 裸 base64 并按 yyyy/MM 归档存储，返回入库用的相对路径。
   * 数据无效或写盘失败返回 null（记录不带照片照常保存）。
   */
  public String saveDataUrl(String imageData) {
    if (imageData == null || imageData.isBlank()) {
      return null;
    }
    String payload = imageData.trim();
    String extension = ".jpg";
    int commaIndex = payload.indexOf(',');
    if (payload.startsWith("data:") && commaIndex >= 0) {
      String mime = payload.substring(5, commaIndex);
      if (mime.contains("png")) {
        extension = ".png";
      } else if (mime.contains("webp")) {
        extension = ".webp";
      }
      payload = payload.substring(commaIndex + 1);
    }

    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(payload);
    } catch (IllegalArgumentException ex) {
      return null;
    }
    if (bytes.length == 0) {
      return null;
    }

    LocalDate today = LocalDate.now();
    Path monthDir = baseDir.resolve(String.format("%04d", today.getYear()))
        .resolve(String.format("%02d", today.getMonthValue()));
    try {
      Files.createDirectories(monthDir);
      Path target = monthDir.resolve(UUID.randomUUID().toString().replace("-", "") + extension);
      Files.write(target, bytes);
      return baseDir.relativize(target).toString().replace('\\', '/');
    } catch (IOException | SecurityException ex) {
      return null;
    }
  }

  /** 供未来读取/展示照片时解析绝对路径；拒绝路径穿越。 */
  public Path resolve(String storedPath) {
    Path resolved = baseDir.resolve(storedPath).normalize();
    if (!resolved.startsWith(baseDir)) {
      throw new IllegalArgumentException("非法照片路径: " + storedPath);
    }
    return resolved;
  }
}
