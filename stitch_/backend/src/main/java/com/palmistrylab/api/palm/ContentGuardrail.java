package com.palmistrylab.api.palm;

import java.util.regex.Pattern;

/**
 * 合规出口审查（TD §13.2）：疾病/死亡/生育预测与具体财务指引绝对禁止。
 * 命中即弃用本次 LLM 文案，回退确定性模板。
 */
public final class ContentGuardrail {

  public static final String GUARDRAIL_TEXT =
      "内容红线（最高优先级）：严禁输出疾病、死亡、寿元、生育相关预测，"
          + "严禁给出股票/基金/加密货币等任何具体财务投资建议；只做性格倾向与娱乐化解读。";

  private static final Pattern BANNED_CLAIM_PATTERN = Pattern.compile(
      "癌症|绝症|肿瘤|白血病|晚期|死亡|死期|寿元|阳寿|短命|活不过|血光之灾|牢狱之灾|难产|克子|克夫"
          + "|暴富|发财|破财|炒股|股票|基金|期货|加密货币|彩票|赌博|加仓|减仓|买入|卖出|翻倍|稳赚");

  private ContentGuardrail() {
  }

  /** 违禁内容审查：任一文本命中红线即返回 true。 */
  public static boolean containsBannedClaims(String... texts) {
    if (texts == null) {
      return false;
    }
    for (String text : texts) {
      if (text != null && BANNED_CLAIM_PATTERN.matcher(text).find()) {
        return true;
      }
    }
    return false;
  }
}
