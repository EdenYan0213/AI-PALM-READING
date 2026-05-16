# 手动画线校对功能 Android 端验收报告

## 1. 验收范围与方法
- 验收对象: `trace_confirm/code.html`、`light_3/code.html`、`backend/src/main/java/com/palmistrylab/api/model/ApiDtos.java`、`backend/src/main/java/com/palmistrylab/api/service/PalmistryService.java`
- 验收方式: 代码静态验收 + 构建校验（`mvn -DskipTests compile` 已通过）
- 说明: 本报告未进行真机微信小程序实测，因此涉及机型性能、手势冲突、弱网体验等场景标记为“无法判断”或“需真机复核”。

## 2. 结论总览
- 总体结论: 条件通过（代码能力已基本到位，P0 视觉项已修复，但仍有真机验证缺口）
- 关键通过点:
  - 三线轨迹采集、分线颜色、清除重画、跳过描绘、`traces` 上报、后端特征提取与 LLM 拼接均已实现。
- 关键问题:
  - A 类语义一致性（如“延伸到手腕”“手掌中部结束”“情感丰富”）当前规则与文案不能稳定保证。
  - Android 兼容性场景（机型/手势/弱网）尚未完成真机验收。

## 3. 按“AI验收执行指令”逐条判定

### 前端检查
1. 是否实现三条线触摸轨迹记录（lifeLine/wisdomLine/loveLine）
- 结论: 通过
- 证据: `trace_confirm/code.html:185`, `trace_confirm/code.html:186`, `trace_confirm/code.html:187`, `trace_confirm/code.html:188`

2. 每条轨迹是否记录坐标点数组，点结构是否包含 x、y
- 结论: 通过
- 证据: `trace_confirm/code.html:370`, `trace_confirm/code.html:373`, `trace_confirm/code.html:374`, `trace_confirm/code.html:375`
- 说明: 实际还包含 `t` 时间戳字段。

3. 三条线描线颜色是否不同（红/蓝/绿）
- 结论: 不通过
- 证据: `trace_confirm/code.html:149`, `trace_confirm/code.html:155`, `trace_confirm/code.html:161`
- 原因: 当前为红橙、蓝青、粉紫，不是“红/蓝/绿”。

4. 是否有“清除重画”按钮，且只清除当前选中的线
- 结论: 通过
- 证据: `trace_confirm/code.html:126`, `trace_confirm/code.html:439`, `trace_confirm/code.html:440`

5. 是否有“跳过描绘”入口
- 结论: 通过
- 证据: `trace_confirm/code.html:84`, `trace_confirm/code.html:444`, `trace_confirm/code.html:445`

6. 切换线型标签时，引导虚线是否对应切换
- 结论: 通过
- 证据: `trace_confirm/code.html:202`, `trace_confirm/code.html:203`, `trace_confirm/code.html:436`

7. 未描完三条线时，解锁按钮是否有拦截逻辑
- 结论: 通过
- 证据: `trace_confirm/code.html:130`, `trace_confirm/code.html:329`, `trace_confirm/code.html:330`

8. 描线时是否有振动反馈调用（wx.vibrateShort）
- 结论: 通过
- 证据: `trace_confirm/code.html:300`, `trace_confirm/code.html:301`

9. Android 系统返回手势冲突是否做了处理（底部区域描线时）
- 结论: 无法判断（代码有基础处理）
- 证据: `trace_confirm/code.html:109`, `trace_confirm/code.html:334`, `trace_confirm/code.html:345`, `trace_confirm/code.html:336`
- 说明: 已做 `touch-none` + `preventDefault` + `pointer capture`，但是否彻底规避安卓系统手势需真机验证。

### 后端检查
10. `/api/v1/palm/analyze` 是否新增 `traces` 参数
- 结论: 通过
- 证据: `backend/src/main/java/com/palmistrylab/api/model/ApiDtos.java:16`, `backend/src/main/java/com/palmistrylab/api/model/ApiDtos.java:21`

11. 是否有将轨迹坐标转为文字描述的函数
- 结论: 通过
- 证据: `backend/src/main/java/com/palmistrylab/api/service/PalmistryService.java:300`, `backend/src/main/java/com/palmistrylab/api/service/PalmistryService.java:365`, `backend/src/main/java/com/palmistrylab/api/service/PalmistryService.java:531`

12. 是否能识别长度、曲率、连续性、分叉、岛纹
- 结论: 通过
- 证据:
  - 长度: `backend/src/main/java/com/palmistrylab/api/service/PalmistryService.java:383`, `backend/src/main/java/com/palmistrylab/api/service/PalmistryService.java:433`
  - 曲率: `backend/src/main/java/com/palmistrylab/api/service/PalmistryService.java:385`, `backend/src/main/java/com/palmistrylab/api/service/PalmistryService.java:443`
  - 连续性: `backend/src/main/java/com/palmistrylab/api/service/PalmistryService.java:387`
  - 分叉: `backend/src/main/java/com/palmistrylab/api/service/PalmistryService.java:389`, `backend/src/main/java/com/palmistrylab/api/service/PalmistryService.java:472`
  - 岛纹: `backend/src/main/java/com/palmistrylab/api/service/PalmistryService.java:390`, `backend/src/main/java/com/palmistrylab/api/service/PalmistryService.java:500`, `backend/src/main/java/com/palmistrylab/api/service/PalmistryService.java:528`

13. traces 为空时是否有降级逻辑，不报错
- 结论: 通过
- 证据: `backend/src/main/java/com/palmistrylab/api/service/PalmistryService.java:300`, `backend/src/main/java/com/palmistrylab/api/service/PalmistryService.java:317`

14. LLM prompt 中是否拼入轨迹描述文字
- 结论: 通过
- 证据: `backend/src/main/java/com/palmistrylab/api/service/PalmistryService.java:580`, `backend/src/main/java/com/palmistrylab/api/service/PalmistryService.java:589`

### 数据格式检查
15. traces 字段数据结构是否正确（含三条线坐标数组）
- 结论: 通过
- 证据: `backend/src/main/java/com/palmistrylab/api/model/ApiDtos.java:24`, `backend/src/main/java/com/palmistrylab/api/model/ApiDtos.java:25`, `backend/src/main/java/com/palmistrylab/api/model/ApiDtos.java:26`, `backend/src/main/java/com/palmistrylab/api/model/ApiDtos.java:27`

16. 示例请求（含 traces 完整 JSON）
- 结论: 已提供

```json
{
  "source": "camera",
  "handSide": "left",
  "gender": "unknown",
  "imageData": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQ...",
  "traces": {
    "lifeLine": [
      { "x": 150.2, "y": 320.5, "t": 1714200000123 },
      { "x": 152.8, "y": 325.1, "t": 1714200000175 }
    ],
    "wisdomLine": [
      { "x": 180.1, "y": 410.9, "t": 1714200001188 },
      { "x": 203.4, "y": 418.6, "t": 1714200001239 }
    ],
    "loveLine": [
      { "x": 210.7, "y": 286.4, "t": 1714200002288 },
      { "x": 252.9, "y": 274.2, "t": 1714200002341 }
    ]
  }
}
```

## 4. Android精简验收标准映射

### 4.1 功能验收清单（F）
- F-01 页面入口: 通过（`ai..._light` 已跳转 `trace_confirm`）
- F-02 引导文案: 通过
- F-03 引导虚线淡红色: 通过（已修复为随线型切换，默认生命线淡红）
- F-04 生命线描线效果: 通过（代码实现，待真机观感复核）
- F-05 智慧线描线效果与保留: 通过
- F-06 感情线描线效果与三线同显: 通过
- F-07 振动反馈: 通过（待真机确认触发稳定性）
- F-08 清除重画: 通过
- F-09 标签切换与虚线切换: 通过
- F-10 跳过描绘: 通过（报告页有“未经确认，仅供参考”）
- F-11 三线完成后出现解锁按钮: 通过
- F-12 未描完拦截: 通过
- F-13 traces 上报: 通过

### 4.2 AI解读准确性（A）
- A-01 长生命线出现“延伸到手腕”: 无法判断（当前规则未强约束该短语）
- A-02 短智慧线出现“手掌中部结束”: 无法判断（当前规则未强约束该短语）
- A-03 弯曲感情线出现“情感丰富”: 无法判断（当前规则未强约束该短语）
- A-04 断续纹路: 通过（规则可识别断续）
- A-05 分叉纹路: 通过
- A-06 岛纹识别: 通过（规则存在，需真机样本复核）
- A-07 跳过降级: 通过
- A-08 互不干扰: 通过（分线独立处理）

### 4.3 Android兼容性（C）
- C-01 主流旗舰流畅性: 无法判断（需真机）
- C-02 中低端可用性: 无法判断（需真机）
- C-03 屏幕尺寸适配: 无法判断（需多机）
- C-04 系统手势冲突: 无法判断（需真机底部上滑复测）
- C-05 Home切后台恢复: 无法判断（需真机）
- C-06 快速滑动掉点: 无法判断（需真机）
- C-07 非常规路径稳定性: 通过（代码未限制路径）
- C-08 弱网提交: 无法判断（需网络环境）
- C-09 LLM异常兜底: 部分通过（后端有 fallback；前端失败提示已做，需联调验证）

## 5. 准出判断（按你给的准出标准）
- 结果: 当前不满足准出
- 原因:
  - C-01、C-02、C-04 尚未完成真机验证，不能判定“全部通过”。

## 6. 建议的最小整改与复测
1. 先修 P0 视觉项: 将引导虚线按当前线型动态上色，生命线默认淡红。
2. 增加语义稳定性: 在后端 `buildNaturalSentence` 增补“手腕/中部/弧形”等模板短语映射，减少 A-01~A-03 波动。
3. 立即组织 Android 真机回归: 至少 2 台不同品牌 + Wi-Fi/4G 两轮，重点压测 C-01/C-02/C-04。
