# 手相研究所 · 技术设计建议（TD V2.0）

> 日期：2026-09-09 ｜ 基于：MVP_PRD_V1.0 + 产品完成度评估（50%）+ 竞品调研（30+ 开源项目）
> 定位：TD 建议稿，用于 brainstorm 对齐后再定稿

---

## 0. 一页纸结论

**现状**：后端骨架完整（14 接口 + LLM 接入 + 埋点），但核心识别层是"规则 + 随机 + 文案拼装"，图片从未被真正分析；会话在内存；无用户体系；主流程前端断链。

**本 TD 的核心主张**：把架构从单层"文案生成器"升级为三层管线——

```
感知层（这张手长什么样）→ 量化层（特征是什么）→ 生成层（怎么说人话）
```

其中**量化层必须确定性**（同一张图同一结果），**生成层才用 LLM**。这是竞品中 538★ 太卜、1143★ Numerologist_skills 验证过的主流最佳实践，也是把我们"演示版"变成"真产品"的关键一步。

**三个方案分叉点**（需要 brainstorm 拍板）：
- 识别路线：A. 纯视觉 LLM ｜ B. MediaPipe 边车 + 规则 ｜ C. 混合（推荐）
- 会话/用户：A. Session 落库 + 设备指纹 ｜ B. 微信 openid 体系
- 流式输出：SSE 打字机 vs 整段返回

---

## 1. 现状盘点（TD 的起点）

### 1.1 已有资产
| 资产 | 状态 | 备注 |
|---|---|---|
| Spring Boot 后端 | ✅ 14 接口 | analyze / unlock-deep / rare-mark / CP / 埋点 / 指标 / 周记录 / 日历 / 月报 |
| LLM 客户端 | ✅ SiliconFlow | Kimi-K2.6 主 + GLM-5.1 兜底，`chat(system, user, imageData)` **已支持图片输入**，90s 超时，失败回退 |
| Rate Limit / CORS / 异常处理 | ✅ | 90 req/min |
| 数据层 | ⚠️ 部分 | JPA + H2(dev)/MySQL(prod)，有 4 个 Entity，但 SessionState 在内存 ConcurrentHashMap |
| 前端 | ⚠️ | 12 个页面变体齐全，trace_confirm 已会调 API，但主流程（light_2→ai→light_3）断链 |
| 校对模式 | ✅ | PalmLineTraces + TracePoint 契约已定义，可承载真实识别结果 |

### 1.2 核心差距（与竞品对照）
| 差距 | 竞品参照 | 本 TD 对应章节 |
|---|---|---|
| 图片未被真正分析（识别=伪） | yeonsumia/palmistry、PALMYSTERY 都真看图 | §3 感知层 |
| 特征不确定（随机数） | Palm-Astro 强调"特征驱动而非随机输出" | §4 量化层 |
| LLM 自由发挥、幻觉不可控 | 太卜：确定性排盘 + LLM 只做解读 | §5 生成层 |
| 无用户身份、记录接口裸奔 | bazi-master：账号体系 | §6 身份与会话 |
| 无真实等待反馈（假进度条） | dreamhunter2333：流式打字机 | §5.4 SSE |
| 分享链路是假的 | kanxiang 撕纸海报是传播利器 | §7 分享引擎 |

---

## 2. 目标架构

```
┌─────────────────────────── 前端（H5，12 页面变体）───────────────────────────┐
│  拍照页 ──图片──▶ 分析页(SSE 进度) ──▶ 报告页 ──▶ 解锁/分享/私域            │
└──────────────┬───────────────────────────────────────────────────────────┘
               │ HTTPS (JSON / SSE)
┌──────────────▼──────────────── Spring Boot 后端 ──────────────────────────┐
│                                                                            │
│  PalmistryController（现有 14 接口 + 新增 4 个，见 §8）                     │
│        │                                                                   │
│  ┌─────▼──────────────────────────────────────────────────────┐            │
│  │ PalmAnalysisOrchestrator（新：分析编排）                     │            │
│  │                                                            │            │
│  │ ① PerceptionService  感知层：这张图是不是一张合格的手掌      │            │
│  │      ├─ 基础校验（现有 validate-image：格式/大小/手掌存在）  │            │
│  │      └─ 手部关键点检测 → 掌心 ROI 裁剪 + 质量分             │            │
│  │                                                            │            │
│  │ ② FeatureService      量化层：确定性特征提取                │            │
│  │      ├─ 掌型分类（土/风/火/水，由长宽比+手指比例规则判定）    │            │
│  │      ├─ 主线特征（生命/智慧/感情：走向/深度/清晰度/断点）     │            │
│  │      └─ 印记检测（凤凰眼等，模板匹配或 LLM 视觉复核）         │            │
│  │      ▶ 输出：PalmFeatureSet（JSON，可持久化、可复现）        │            │
│  │                                                            │            │
│  │ ③ NarrativeService     生成层：LLM 文案                     │            │
│  │      ├─ PromptAssembler：特征 JSON + 相术知识库片段 + 人设   │            │
│  │      ├─ LlmClientService（现有，升级：加 SSE 流式方法）      │            │
│  │      └─ OutputParser：强制 JSON Schema 校验，失败重试        │            │
│  │                                                            │            │
│  │ ④ CpEngine（现有）→ 改为读两人的 PalmFeatureSet 做规则匹配  │            │
│  └────────────────────────────────────────────────────────────┘            │
│        │                          │                                        │
│  Persistence（SessionState 落库）   EventTrack（现有）                      │
└────────────────────────────────────────────────────────────────────────────┘
```

**设计原则**
1. **确定性优先**：FeatureService 不用随机数；同一张图 24h 内重复分析返回相同 FeatureSet（图哈希缓存）。
2. **每层可降级**：感知失败→提示重拍；量化失败→退化为掌型-only 特征集；LLM 失败→现有模板文案兜底。
3. **契约先行**：PalmFeatureSet 先定 JSON Schema，前后端与 CP 引擎都以它为唯一事实源。

---

## 3. 感知层设计（方案分叉点 ①）

三个候选方案（竞品三种路线的映射）：

| | 方案 A：纯视觉 LLM | 方案 B：MediaPipe 边车 | 方案 C：混合（推荐） |
|---|---|---|---|
| 做法 | 掌图直接喂多模态模型（Kimi/ GLM-4.5V），prompt 让它输出特征 JSON | Python 微服务跑 MediaPipe Hands（ONNX 版可脱离 mediapipe 依赖）做关键点 + ROI + OpenCV 特征 | B 做客观特征（掌型/ROI/线走向），A 做视觉复核（印记/线质感的细节） |
| 开发量 | 极小（1-2 天） | 中（需新增 Python 服务 + 部署） | 中偏大 |
| 确定性 | 差（同图不同答） | 好（纯算法） | 好 |
| 成本 | 每次调用都花 token | 几乎为零 | 中 |
| 印记检测（凤凰眼等） | 强 | 弱（模板匹配难覆盖） | 强 |
| 部署复杂度 | 无新增 | +1 个服务（本机 M1 可跑，CPU 推理 OK） | +1 个服务 |

**推荐：V0.1 用方案 A 快速闭环（把现有 `chat(system,user,imageData)` 用起来，主流程先接真）→ V0.2 加方案 B 的边车服务补确定性特征 → 形成方案 C。**

方案 B 边车规格（预留）：
- `POST /detect`：入参图片 base64 → 出参 `{landmarks: [21 points], palmBox, roiJpeg, qualityScore}`（参照 yakhyo/mediapipe-hand-landmark-onnx、brianwalczak/palm_roi 的做法）
- FastAPI + 单容器，内存占用 < 500MB，超时 3s

---

## 4. 量化层设计：PalmFeatureSet（核心契约）

```jsonc
{
  "version": "1.0",
  "imageHash": "sha256:...",          // 幂等缓存键
  "palmShape": {                       // 掌型
    "type": "water",                   // earth|wind|fire|water
    "confidence": 0.86,
    "basis": {"palmRatio": 1.24, "fingerRatio": 1.08}   // 判定依据，可解释
  },
  "lines": {                           // 三主线 + 事业线
    "life":   {"presence": true, "depth": "deep", "clarity": "clear",
               "breaks": 0, "chains": true, "curvature": "moderate"},
    "head":   {"presence": true, "slope": "downward", "forkEnd": false, "...": "..."},
    "heart":  {"...": "..."},
    "fate":   {"presence": false, "...": "..."}
  },
  "marks": [                           // 稀有印记
    {"name": "phoenix_eye", "found": true, "position": "thumb_joint", "confidence": 0.72}
  ],
  "quality": {"blur": 0.18, "exposure": "ok", "occlusion": "none", "retake": false}
}
```

**规则引擎示例**（对应 PRD 5.1）：
- 掌型：`掌宽/掌长 > 1.05 且 手指/掌长 < 0.75 → 土型手`（阈值参数化，配置文件管理）
- 主线：深度/清晰度由边缘对比度分档；断点数由线段连通分量计数
- **所有阈值进 `palmistry-rules.yml`，不写死在代码里**——这是后续调优和 A/B 的抓手

**CP 引擎改造**：现有 CpEngine 的匹配度 70%-98% 随机区间 → 改为基于双方 FeatureSet 的规则计算（如：掌型相生 +N、主线互补 +N），随机性只允许存在于 ±2% 的"手感噪声"。组合名从"掌型×主线标签"的映射表选取。

---

## 5. 生成层设计：LLM 文案

### 5.1 Prompt 结构（三段式）
```
[System 人设]  赛博玄学风格 + 输出 JSON Schema + 禁忌清单（不预测死亡/疾病/财务建议）
[Knowledge]    相术知识库按特征检索的片段（如 chains=true → 检索"生命线锁链状"条目，
               来源：麻衣神相白话版 + 我们自己的梗化文案，存 resources/lore/*.md）
[Feature JSON] 上层输出的 PalmFeatureSet（原样注入，LLM 不做任何"识别"）
```

### 5.2 强约束输出
- 强制 JSON Schema 校验（Jackson 反序列化失败即重试，最多 2 次）
- 每个解读条目必须引用至少 1 个输入特征（prompt 里要求 `"basedOn": ["life.chains"]`，后端校验引用合法性）——抄 Numerologist_skills 的防幻觉思路，成本低效果好

### 5.3 稀有印记触发逻辑
- 印记由量化层给出 `found + confidence`，**是否展示仍按产品逻辑概率触发**（PRD 的稀缺性设计），但触发后文案必须基于真实检测结果

### 5.4 流式输出（方案分叉点 ③）
- 新增 `POST /palm/analyze/stream`（SSE）：事件序列 `progress(stage) → token(delta) → done(result)`
- 前端分析页从"假动画"改为真进度（感知→量化→生成三阶段）+ 打字机输出
- 备选：保持同步接口 + 前端模拟进度，实现简单但体验差，不推荐

---

## 6. 身份、会话与安全（方案分叉点 ②）

### 6.1 会话持久化（P0）
- `SessionState` 从 ConcurrentHashMap 迁到数据库：新增 `analysis_session` 表（session_id, user_key, feature_set JSON, narrative JSON, status, created_at, expire_at），72h 过期清理
- 复用现有 SessionRecordEntity 或新建均可，倾向新建（职责分离）

### 6.2 身份方案
| 阶段 | 方案 | 说明 |
|---|---|---|
| V0.1 | 设备指纹 | 前端生成 UUID 存 localStorage + 后端签发 HMAC token，记录接口只认 token 对应 user_key |
| V1.0 | 手机号/微信登录 | H5 场景建议微信公众号网页授权（openid），顺带解决分享回流归因 |

- 所有 `/record/*` 接口增加 `Authorization` 校验（现状：任何人传 userId 就能查任何人的数据）

### 6.3 合规（上线硬门槛）
- 掌纹照片 = 敏感个人信息：上传前单独同意弹层 + 隐私政策页 + 图片"仅用于本次分析"声明与 24h 删除机制（imageHash 缓存除外，需注明）
- 全站免责声明："仅供娱乐，不构成医疗/财务/人生建议"
- 图片传输全链路 HTTPS，落盘可选（默认只存特征 JSON，不存原图）

---

## 7. 分享引擎（增长核心，竞品验证）

- 后端新增 `POST /share/card`：入参 sessionId → 服务端用 Java2D/Thymeleaf 渲染分享图（掌型徽章 + 主线标签 + 匹配度/组合名 + 小程序码/二维码）→ 返回图片 URL 或 base64
- 卡片模板 2-3 套（对应报告皮肤 V1.1 方向），参考 kanxiang 的"撕纸海报"创意做赛博风
- 分享落地页 = 报告页公开摘要版（免登录可看 3 秒亮点，引导重新分析）——这是裂变闭环的关键，当前完全缺失

---

## 8. API 契约变更清单

| 接口 | 变更类型 | 说明 |
|---|---|---|
| `POST /api/v1/palm/analyze` | 修改 | 入参增加 `clientTrace?`（校对模式的点位，可反哺量化层）；响应增加 `featureSet`（前端报告页直接消费） |
| `POST /api/v1/palm/analyze/stream` | **新增** | SSE 流式版本（§5.4） |
| `POST /api/v1/palm/validate-image` | 修改 | 响应增加 `quality` 明细与 `retake` 建议文案 |
| `POST /api/v1/auth/device` | **新增** | 设备指纹换 token |
| `POST /api/v1/share/card` | **新增** | 分享卡片生成 |
| `GET /api/v1/record/*` | 修改 | 全部增加 token 鉴权 |
| `POST /api/v1/cp/analyze` | 修改 | 入参改为两侧 sessionId（读已落库的 FeatureSet），替代前端拼 CPUser |
| 现有 14 接口 | 兼容 | unlock-deep / rare-mark / events / metrics 不动 |

## 9. 数据模型增量

```sql
analysis_session(id, session_id UK, user_key, image_hash, feature_set JSON,
                 narrative JSON, thumb_ref, status ENUM, created_at, expire_at)
-- thumb_ref 指向对象存储/本地目录中的低清缩略图；原图不落盘；
-- 提供用户删除入口（删 session 行 + 缩略图文件）
user_identity(id, user_key UK, id_type ENUM(DEVICE/WECHAT), id_value, created_at)
share_card(id, session_id, template, url, created_at)
-- 现有 4 Entity 不动；prod 需引入 Flyway 管理（现状 ddl-auto=validate 但无迁移脚本，P1）
```

## 10. 非功能设计

| 项 | 设计 | 依据 |
|---|---|---|
| 性能 | 分析全链路 P95 < 15s：感知 3s + 量化 1s + 生成 8s；SSE 消除体感等待 | 完成度评估：真实 LLM 8-15s |
| 成本 | 方案 A 每次分析约 3-5k tokens（图 + 文）；图哈希缓存使重复分析零成本；Fallback 模型兜底 | 竞品 kanxiang 同量级 2-5k |
| 部署 | backend + (V0.2)perception 边车 双容器 docker-compose；字体换国内 CDN | 完成度评估 P1 |
| 观测 | 分析链路分阶段打点（perception/feature/narrative 各自耗时与失败率），进现有 metrics | |
| 测试 | FeatureService 纯规则 → 单测覆盖率高；LLM 层用契约测试（固定 FeatureSet → 断言 JSON Schema）；保留现有 mock 测试模式 | 用户已有真实+mock 双测试习惯 |

## 11. 里程碑

| 里程碑 | 内容 | 出口标准 |
|---|---|---|
| **TD-V0.1 主流程接真**（1-1.5 周） | 方案 A 视觉 LLM 接入 analyze；SessionState 落库；主流程前端接线（light_2→AI→light_3 真调接口）；设备 token | 同一张手两次分析结果一致；全流程无演示兜底数据 |
| **TD-V0.2 确定性升级**（2 周） | MediaPipe 边车 + FeatureService 规则引擎 + PalmFeatureSet 契约 + CP 引擎改造 + SSE | 特征 100% 确定性；分析页真进度 |
| **TD-V1.0 可上线**（2 周） | 分享引擎 + 合规三件套（同意/隐私/免责）+ Flyway + Docker + 微信内实测 | 完成度评估 P0 清零，独立 H5 可备案上线 |

## 12. 开放问题与决策记录（2026-09-09 brainstorm）

### 已拍板 ✅
1. **识别路线 → 方案 A 先行，V0.2 再上边车**：V0.1 用纯视觉 LLM（现有 `chat(system,user,imageData)`）把主流程接真；V0.2 引入 MediaPipe 边车补确定性特征，形成混合 C。
2. **玄与真的边界 → 特征确定 + 展示层留玄感**：量化层 100% 确定性（同图同特征）；CP 匹配度保留 ±2% 手感噪声；稀有印记保持产品级概率触发（但触发后文案必须基于真实检测结果）。
3. **图片存储 → 存缩略图 + 可删**：落盘低清缩略图（用于报告回看、皮肤复渲染、分享卡），原图分析完即弃；缩略图与用户可一键删除，隐私政策注明保留策略。

### 待讨论 ⏳（13 节评审后仅剩 1 项半）
4. **FeatureSet 粒度**：结构特征（presence/走向/掌型）已定；质感特征（depth/clarity/breaks）改由视觉 LLM 辅助标注 low-confidence——唯一遗留：事业线/掌丘要不要单独建条目（V0.2 再议）
5. ~~视觉模型选型~~ → 已裁决：视觉与文本拆开（Qwen2.5-VL / GLM-4.5V 看图，Kimi 写文案），见 §13.1
6. ~~双域名轮换~~ → 已裁决：降为低优先级，留备案备用域名即可，主战场站外种草，见 §13.3

---

## 13. 多视角评审结论（2026-09-09 四方 agent brainstorm 裁决）

评审视角：CV 工程 / LLM 应用 / 增长产品 / 合规安全。裁决采纳的修正如下：

### 13.1 修正原 TD 的高估项
| 原设计 | 评审结论 | 修正 |
|---|---|---|
| V0.1 出口标准"同图两次分析结果一致" | 视觉 LLM 固有做不到，自相矛盾 | 降级为"**同图缓存命中即一致**"（图哈希缓存是最有效的一致性手段） |
| MediaPipe 边车"补确定性特征" | 21 骨架点对**掌纹提取几乎无用**，只对 ROI/质量/掌型几何有用 | 边车范围收窄为：手掌检测 + ROI 裁剪 + 质量分 + 掌长宽比（掌型判定）。**掌纹主线不做传统 CV 提取**（学术难题，精度<70%，2 周不现实） |
| `lines.depth/clarity/breaks` 进确定性特征 | 传统 CV 做不稳（光照敏感、噪声线不可分），视觉 LLM 也是"看图编" | 拆分：**结构特征确定**（presence/走向/掌型），**质感特征 LLM 辅助**并标记 low-confidence，规则引擎只消费结构特征 |
| basedOn 引用校验防幻觉 | 只能防"捏造引用"，防不住语义级夸大 | 改为**检索驱动生成**：知识库条目按特征键检索、LLM 只做连接与润色；另加违禁词黑名单（死亡/疾病/财务）双重保险 |
| Kimi-K2.6 一体机方案 | 文本旗舰但视觉对细纹理偏弱 | **视觉与文本拆开**：Qwen2.5-VL-72B 或 GLM-4.5V 专管看图提特征（快），Kimi 专管文案；fallback：视觉兜底 GLM-4.5V → 模板文案终兜底 |
| 免责声明 = 合规护身符 | 对"内容定性"几乎无用 | 见 13.2 合规硬升级 |

### 13.2 合规硬升级（P0，上线门槛）
1. **掌纹（含缩略图）按"生物识别信息"最高级别处理**：低清 ≠ 脱敏。上传前强制单独同意弹层（非一揽子勾选）、拒绝后可基本使用。
2. **调用第三方 LLM API 属"向第三方提供"**：隐私政策必须列明接收方（SiliconFlow 及模型方）、目的；签委托处理协议、书面承诺不留存不训练。
3. **内容红线进 prompt 禁忌清单 + 出口词表审查**：疾病/死亡/生育预测、具体财务投资指引绝对禁止，文案只做"性格倾向 + 娱乐化"。
4. 备案类目避开"占卜"类目；设备指纹用弱指纹（UUID）并告知；openid 仅微信内可静默获取，保留设备 token 主路径。

### 13.3 增长侧修正（低成本高杠杆）
1. **确定性做成玩法而非隐藏**："手相档案"（复核："手没变，是你变了"）、情侣双图对比、月度"手相进化论"。
2. **解锁点迁移**：从"解锁深度报告"改为 **CP 全文 / 稀有印记验真（天然衔接私域）/ 手相年运**；免费层留一个最大悬念不闭合。
3. **分享卡放人格标签不放数字**：标签引发身份认同转发（MBTI 逻辑）；三要素 = 稀有人格标签 + 梗文案 + 悬空信息钩子。
4. **TD 遗漏项补进 backlog**：截图友好排版 + 内置水印码（用户截屏多于点分享）；CP 单向发起（"测 TA"）；落地页摘要回流设计；分享卡 A/B 通道。
5. 双域名轮换降为低优先级，备案备用域名即可，主战场在抖音/小红书站外种草。

### 13.4 SSE 裁决
保留 V0.2 计划但降级优先级：RestTemplate 不支持流式，需在新 stream 接口单点用 OkHttp/WebClient + SseEmitter（约 1 天）；**过渡方案**：保留同步接口 + 前端按感知/量化/生成三阶段真实回调模拟进度，可解决 80% 体验问题。

### 13.5 四方一致同意的点
- 采集端约束（拍照引导框、光线检测）比后端任何算法都划算 → 提升为 V0.1 任务
- 感知层（ROI + 质量分）是真正地基，其价值被原 TD 低估
- 图哈希缓存必须配单独同意（掌纹缓存 = 生物特征缓存）
- 兜底模板文案与 LLM 文案的风格一致性需要专门设计（few-shot 固化语气）
