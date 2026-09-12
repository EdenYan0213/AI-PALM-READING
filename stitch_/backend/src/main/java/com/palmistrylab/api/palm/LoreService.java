package com.palmistrylab.api.palm;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 相术知识库（V0.2 检索驱动生成, TD §13.1）。
 *
 * 条目存于 classpath:lore/{palmshape|mark}/*.md，按特征键检索后整段注入
 * LLM prompt —— LLM 只做连接与润色，不做自由发挥，防语义级幻觉。
 */
@Service
public class LoreService {

  static final Map<String, String> HAND_TYPE_KEYS = Map.of(
      "土型手", "earth",
      "风型手", "wind",
      "火型手", "fire",
      "水型手", "water");

  private final Map<String, String> entries = new ConcurrentHashMap<>();

  @PostConstruct
  void loadLoreEntries() {
    try {
      PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
      Resource[] resources = resolver.getResources("classpath:lore/**/*.md");
      for (Resource resource : resources) {
        String filename = resource.getFilename();
        if (filename == null || !filename.endsWith(".md")) {
          continue;
        }
        String key = filename.substring(0, filename.length() - 3);
        entries.put(key, new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
      }
    } catch (Exception ex) {
      // 知识库加载失败不影响主流程：检索结果为空时 LLM 退回特征注入模式。
    }
  }

  /** 按掌型与印记检索知识条目，未命中返回空串。 */
  public String retrieve(String handType, String rareMark) {
    StringBuilder block = new StringBuilder();
    String shapeKey = handType == null ? null : HAND_TYPE_KEYS.get(handType);
    if (shapeKey != null && entries.containsKey(shapeKey)) {
      block.append(entries.get(shapeKey)).append("\n\n");
    }
    String markKey = markKey(rareMark);
    if (markKey != null && entries.containsKey(markKey)) {
      block.append(entries.get(markKey));
    }
    return block.toString().trim();
  }

  public boolean isLoaded() {
    return !entries.isEmpty();
  }

  private String markKey(String rareMark) {
    if (rareMark == null) {
      return null;
    }
    return switch (rareMark) {
      case "凤凰眼" -> "phoenix_eye";
      case "双鱼纹" -> "double_fish";
      case "神秘十字" -> "mystery_cross";
      case "太阳环" -> "sun_ring";
      default -> null;
    };
  }
}
