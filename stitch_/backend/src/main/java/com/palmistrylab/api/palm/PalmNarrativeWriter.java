package com.palmistrylab.api.palm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palmistrylab.api.llm.LlmClient;
import com.palmistrylab.api.palm.dto.AnalyzeProgressListener;
import com.palmistrylab.api.palm.dto.DeepSection;
import com.palmistrylab.api.palm.dto.PalmLineSummary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * 手相叙事生成（TD §2 NarrativeService 层）：提示词组装、LLM 流式/非流式生成、
 * 输出解析（严格 JSON → 宽松 JSON 块 → 纯文本兜底）、违禁内容过滤。
 */
@Service
public class PalmNarrativeWriter {

  private static final Pattern SENTENCE_SPLIT = Pattern.compile("[。！？!?\\n]+\\s*");

  private final LlmClient llmClient;
  private final LoreService loreService;
  private final ObjectMapper objectMapper;

  public PalmNarrativeWriter(LlmClient llmClient, LoreService loreService, ObjectMapper objectMapper) {
    this.llmClient = llmClient;
    this.loreService = loreService;
    this.objectMapper = objectMapper;
  }

  public record LlmPalmOverview(
      List<String> personalityTags,
      List<PalmLineSummary> freeOverview,
      String teaser) {
  }

  public record LlmPalmOverviewResult(
      boolean used,
      String status,
      LlmPalmOverview overview,
      String rejectReason) {
  }

  /** 单人总览生成：图片校验并入同一次视觉模型调用（imageAccepted 字段）。 */
  public LlmPalmOverviewResult generatePalmOverview(
      String handType,
      List<String> tags,
      String rareMark,
      String imageData,
      String recordMode,
      String tracePromptBlock,
      AnalyzeProgressListener listener) {
    if (!llmClient.isAvailable()) {
      return new LlmPalmOverviewResult(false, "llm_disabled", null, null);
    }

    String systemPrompt = "你是手相研究所的资深解读师，风格专业、具体、可验证。优先返回严格 JSON，不要 markdown。"
        + ContentGuardrail.GUARDRAIL_TEXT;
    String loreBlock = loreService.retrieve(handType, rareMark);
    String userPrompt = "请基于输入生成 JSON，字段结构必须是"
        + "{\"imageAccepted\":true,\"rejectReason\":\"\",\"personalityTags\":[\"\",\"\",\"\"],\"freeOverview\":[{\"lineName\":\"感情线\",\"tags\":\"\",\"shortInterpretation\":\"\"},{\"lineName\":\"智慧线\",\"tags\":\"\",\"shortInterpretation\":\"\"},{\"lineName\":\"生命线\",\"tags\":\"\",\"shortInterpretation\":\"\"}],\"teaser\":\"\"}。"
        + "先校验图片：如果图片主体不是一只清晰的人类手掌正面，imageAccepted 必须为 false，"
        + "rejectReason 固定为“这张图片不是手相，请上传清晰的手掌照片。”，其余字段留空；"
        + "是手掌则 imageAccepted 为 true、rejectReason 为空字符串，并生成完整解读。"
        + "输入：handType=" + handType + "，tags=" + String.join("/", tags) + "，rareMark=" + rareMark + "。"
        + "recordMode=" + recordMode + "。"
        + (tracePromptBlock == null || tracePromptBlock.isBlank() ? "" : "\\n" + tracePromptBlock)
        + (loreBlock.isBlank() ? "" : "\n[Knowledge]\n" + loreBlock + "\n以上知识条目是唯一的相术依据，只做润色与连接，不得输出条目之外的相术断言。")
        + "要求：中文、语气可信。每条 shortInterpretation 120-180 字，必须包含线条形态描述+性格/状态解读+具体可执行建议(动作+频次)+时间窗口或适用情境。"
        + "tags 用 2-4 个短语，用 / 分隔。teaser 40-70 字，提示稀有印记但不泄露完整解析。避免空泛套话。";

    try {
      String content = generateContentStreaming(systemPrompt, userPrompt, imageData, listener);
      try {
        JsonNode node = parseJson(content);
        // 校验与解读合并为同一次调用（省一次 8-15s 的视觉模型往返）。
        if (!node.path("imageAccepted").asBoolean(true)) {
          String rejectReason = com.palmistrylab.api.common.TextUtils.normalizeText(
              node.path("rejectReason").asText("这张图片不是手相，请上传清晰的手掌照片。"), 64);
          if (rejectReason.isBlank()) {
            rejectReason = "这张图片不是手相，请上传清晰的手掌照片。";
          }
          return new LlmPalmOverviewResult(false, "image_rejected", null, rejectReason);
        }
        List<String> llmTags = List.of(
            node.path("personalityTags").path(0).asText(tags.get(0)),
            node.path("personalityTags").path(1).asText(tags.get(1)),
            node.path("personalityTags").path(2).asText(tags.get(2)));

        List<PalmLineSummary> llmOverview = List.of(
            new PalmLineSummary(
                node.path("freeOverview").path(0).path("lineName").asText("感情线"),
                node.path("freeOverview").path(0).path("tags").asText("平稳"),
                node.path("freeOverview").path(0).path("shortInterpretation")
                  .asText("你的感情线走向更偏平稳，末端微上扬，说明你在关系里倾向稳步投入、重视长期一致性。若近期沟通出现回避迹象，建议每周固定1次20分钟对话复盘，按‘事实-感受-需求’三步走，减少误读。适用情境：关系进入磨合期或工作压力上升时。")),
            new PalmLineSummary(
                node.path("freeOverview").path(1).path("lineName").asText("智慧线"),
                node.path("freeOverview").path(1).path("tags").asText("分叉"),
                node.path("freeOverview").path(1).path("shortInterpretation")
                  .asText("你的智慧线较清晰且末端分叉，代表理性拆解与创意延展并行。若近期决策反复，建议用‘3指标对照法’：成本/收益/可逆性各列1条证据，再做取舍。适用情境：项目选型、职业路径切换或学业方向调整期。")),
            new PalmLineSummary(
                node.path("freeOverview").path(2).path("lineName").asText("生命线"),
                node.path("freeOverview").path(2).path("tags").asText("中深"),
                node.path("freeOverview").path(2).path("shortInterpretation")
                  .asText("你的生命线弧度饱满且深度适中，说明体能底盘稳定，但在高压阶段易出现延迟性疲劳。建议连续工作2天后安排1次30分钟低强度运动与拉伸，固定睡眠窗口，减少能量透支。适用情境：连续加班或高密度输出周期。")));

        String llmTeaser = node.path("teaser").asText("检测到稀有印记「" + rareMark + "」，完整解析可在报告页查询");
        LlmPalmOverview overview = new LlmPalmOverview(llmTags, llmOverview, llmTeaser);
        if (ContentGuardrail.containsBannedClaims(overviewTexts(overview))) {
          return new LlmPalmOverviewResult(false, "banned_content_filtered", null, null);
        }
        return new LlmPalmOverviewResult(true, "ok_json", overview, null);
      } catch (Exception parseError) {
        LlmPalmOverview textFallback = buildPalmOverviewFromText(content, tags, rareMark);
        if (textFallback != null) {
          if (ContentGuardrail.containsBannedClaims(overviewTexts(textFallback))) {
            return new LlmPalmOverviewResult(false, "banned_content_filtered", null, null);
          }
          return new LlmPalmOverviewResult(true, "ok_text", textFallback, null);
        }
        return new LlmPalmOverviewResult(false, "parse_failed:" + shortReason(parseError), null, null);
      }
    } catch (Exception callError) {
      return new LlmPalmOverviewResult(false, "call_failed:" + shortReason(callError), null, null);
    }
  }

  /** 深度解锁三段生成；返回 null 表示模型不可用/失败，由调用方回退模板。 */
  public List<DeepSection> generateSingleDeep(String handType, String rareMark) {
    if (!llmClient.isAvailable()) {
      return null;
    }

    String systemPrompt = "你是手相研究所的深度报告引擎。必须返回严格 JSON 数组，不要 markdown。"
        + ContentGuardrail.GUARDRAIL_TEXT;
    String userPrompt = "请返回 JSON 数组 sections，长度3。每个元素结构"
        + "{\"title\":\"\",\"detail\":\"\",\"cyberTip\":\"\"}。"
        + "输入：handType=" + handType + " rareMark=" + rareMark + "。"
        + "要求：中文，detail 90-160 字，要体现趋势+触发条件+行动建议；cyberTip 28-60 字。";

    try {
      String content = llmClient.chat(systemPrompt, userPrompt);
      JsonNode root = parseJson(content);
      JsonNode sectionsNode = root;
      if (!sectionsNode.isArray()) {
        sectionsNode = root.path("sections");
      }
      if (!sectionsNode.isArray() || sectionsNode.size() < 2) {
        return null;
      }
      List<DeepSection> sections = new ArrayList<>();
      for (JsonNode item : sectionsNode) {
        sections.add(new DeepSection(
            item.path("title").asText("深度趋势"),
            item.path("detail").asText("近期处于波段上行区间，建议保持稳定节奏。"),
            item.path("cyberTip").asText("先稳住作息，再放大关键行动。")));
      }
      return sections;
    } catch (Exception ignored) {
      return null;
    }
  }

  /** 解析模型输出为 JSON：严格解析失败时尝试提取 markdown 代码块/括号配平块。 */
  public JsonNode parseJson(String content) throws Exception {
    try {
      return objectMapper.readTree(content);
    } catch (Exception ignored) {
      String extracted = extractJsonBlock(content);
      if (extracted == null || extracted.isBlank()) {
        throw ignored;
      }
      return objectMapper.readTree(extracted);
    }
  }

  private String[] overviewTexts(LlmPalmOverview overview) {
    String[] texts = new String[1 + overview.freeOverview().size()];
    texts[0] = overview.teaser();
    for (int i = 0; i < overview.freeOverview().size(); i++) {
      texts[i + 1] = overview.freeOverview().get(i).shortInterpretation();
    }
    return texts;
  }

  /**
   * 优先流式生成并把真实生成进度回调给 listener（SSE 用）；
   * 流式失败时回退到带完整重试链的非流式调用。
   */
  private String generateContentStreaming(
      String systemPrompt,
      String userPrompt,
      String imageData,
      AnalyzeProgressListener listener) {
    if (listener == AnalyzeProgressListener.NONE) {
      return llmClient.chat(systemPrompt, userPrompt, imageData);
    }
    AtomicInteger generated = new AtomicInteger();
    AtomicLong lastSentAt = new AtomicLong();
    try {
      return llmClient.chatStream(systemPrompt, userPrompt, imageData, delta -> {
        int chars = generated.addAndGet(delta.codePointCount(0, delta.length()));
        long now = System.currentTimeMillis();
        long last = lastSentAt.get();
        if (now - last >= 600 && lastSentAt.compareAndSet(last, now)) {
          listener.onGeneratedChars(chars);
        }
      });
    } catch (Exception streamError) {
      return llmClient.chat(systemPrompt, userPrompt, imageData);
    }
  }

  private LlmPalmOverview buildPalmOverviewFromText(String content, List<String> tags, String rareMark) {
    if (content == null || content.isBlank()) {
      return null;
    }

    List<String> sentences = new ArrayList<>();
    for (String segment : SENTENCE_SPLIT.split(content)) {
      String s = segment == null ? "" : segment.trim();
      if (!s.isBlank()) {
        sentences.add(s);
      }
    }
    if (sentences.isEmpty()) {
      return null;
    }

    String s1 = sentences.get(0) + "。";
    String s2 = (sentences.size() > 1 ? sentences.get(1)
      : "你的智慧线偏理性与结构化，适合先搭框架再填细节；遇到关键选择时建议列出成本/收益/可逆性三项证据，再做取舍，并在一周内复盘结果以校准判断") + "。";
    String s3 = (sentences.size() > 2 ? sentences.get(2)
      : "生命线显示恢复节律较稳定，但高压期后易出现延迟疲劳，建议连续工作2天后安排1次30分钟低强度运动与拉伸，并固定睡眠窗口以稳住体能基线") + "。";

    List<PalmLineSummary> overview = List.of(
        new PalmLineSummary("感情线", "模型文本解析", s1),
        new PalmLineSummary("智慧线", "模型文本解析", s2),
        new PalmLineSummary("生命线", "模型文本解析", s3));

    String teaser = "检测到稀有印记「" + rareMark + "」，模型已完成文本解读，完整趋势可继续解锁查看。";
    return new LlmPalmOverview(tags, overview, teaser);
  }

  private String extractJsonBlock(String text) {
    if (text == null) {
      return null;
    }

    String trimmed = text.trim();
    if (trimmed.startsWith("```")) {
      int firstBreak = trimmed.indexOf('\n');
      int lastFence = trimmed.lastIndexOf("```");
      if (firstBreak > -1 && lastFence > firstBreak) {
        trimmed = trimmed.substring(firstBreak + 1, lastFence).trim();
      }
    }

    int startObj = trimmed.indexOf('{');
    int startArr = trimmed.indexOf('[');
    int start = -1;
    char opener = '\0';
    if (startObj >= 0 && (startArr < 0 || startObj < startArr)) {
      start = startObj;
      opener = '{';
    } else if (startArr >= 0) {
      start = startArr;
      opener = '[';
    }

    if (start < 0) {
      return null;
    }

    char closer = opener == '{' ? '}' : ']';
    int depth = 0;
    boolean inString = false;
    boolean escaping = false;
    for (int i = start; i < trimmed.length(); i++) {
      char c = trimmed.charAt(i);
      if (inString) {
        if (escaping) {
          escaping = false;
        } else if (c == '\\') {
          escaping = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }

      if (c == '"') {
        inString = true;
        continue;
      }

      if (c == opener) {
        depth++;
      } else if (c == closer) {
        depth--;
        if (depth == 0) {
          return trimmed.substring(start, i + 1);
        }
      }
    }
    return null;
  }

  private String shortReason(Exception ex) {
    if (ex == null) {
      return "unknown";
    }
    String msg = ex.getMessage();
    if (msg == null || msg.isBlank()) {
      return ex.getClass().getSimpleName();
    }
    msg = msg.replaceAll("\\s+", " ").trim();
    if (msg.length() > 96) {
      return msg.substring(0, 96);
    }
    return msg;
  }
}
