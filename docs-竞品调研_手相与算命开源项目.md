# 竞品调研：AI 手相与算命类开源项目

> 调研日期：2026-09-09 ｜ 数据来源：GitHub 搜索（按 star 排序）+ 重点项目 README 精读
> 本项目参考：Palmistry Lab（Spring Boot 后端 + 静态 HTML 前端的 AI 手相分析原型）

---

## 一、直接同类：AI 手相项目

| 项目 | Star | 技术栈 | 做法概述 |
|---|---|---|---|
| [yeonsumia/palmistry](https://github.com/yeonsumia/palmistry) | 52 | Python / PyTorch / MediaPipe | **学术派标杆**。四步流水线：① MediaPipe 提取手部关键点 → 对倾斜手掌做透视矫正；② 深度学习模型分割掌纹主线；③ K-means 聚类给每个像素分配到具体线；④ 用关键点做阈值测量各线长度。解决了视角和光照变化问题 |
| [timerzz/kanxiang（看相技能）](https://github.com/timerzz/kanxiang) | 17 | Claude Code Skill（纯提示词工程） | **中文相术知识库路线**。不训练任何模型，直接用视觉 LLM + 《麻衣神相》《冰鉴》等古籍知识做提示词，支持面相/手相/骨相/体相，输出 Markdown 报告。每次分析约 2k-5k tokens |
| [ooorlandooo/palmy](https://github.com/ooorlandooo/palmy) | 19 | Java | 数字图像处理 + 计算机视觉策略的手相 app（与我们同为 Java 技术栈，值得细看） |
| [SairajTripathy-0077/PALMYSTERY](https://github.com/SairajTripathy-0077/PALMYSTERY) | 4 | React+Vite / Express / **Gemini 1.5 Flash** | **最简 LLM 路线**。掌心照片直接丢给多模态 LLM，零 CV 前处理，生成"毒舌吐槽风"手相解读（感情线/头脑线/生命线/命运线）。产品差异化靠人设 |
| [lakshay102/Palm-Astro-Application](https://github.com/lakshay102/Palm-Astro-Application) | 3 | PyTorch / U-Net + ResNet18 / Gradio | **可解释特征路线**。U-Net 分割生命/头脑/感情三大线 → 提取几何特征（长度、曲率、角度、交点数、覆盖率）→ 规则分类主导线、掌型。强调"特征驱动的推理而非随机输出" |
| [withlovee/Horo](https://github.com/withlovee/Horo) | 3 | Java / Android | Android 端计算机视觉手相 app |
| [chaitanyaamle/palm_reading_chatbot_proto](https://github.com/chaitanyaamle/palm_reading_chatbot_proto) | 3 | Flutter | 手相聊天机器人原型 |
| [brianwalczak/palm_roi](https://github.com/brianwalczak/palm_roi) | 1 | Python / MediaPipe | 掌心 ROI 提取与图像增强，可作为前处理组件 |
| [yakhyo/mediapipe-hand-landmark-onnx](https://github.com/yakhyo/mediapipe-hand-landmark-onnx) | 1 | PyTorch / ONNX | MediaPipe Hands 的 ONNX 版（21 个 3D 关键点 + 掌心检测），方便脱离 MediaPipe 依赖部署 |

## 二、算命大类：AI 命理平台（手相常是其中一个模块）

| 项目 | Star | 技术栈 | 做法概述 |
|---|---|---|---|
| [hhszzzz/taibu（太卜）](https://github.com/hhszzzz/taibu) | 538 | Next.js 16 + TS + Supabase + Vercel | **最全的中文开源命理平台**。八字/六爻/紫微/奇门/大六壬/梅花/塔罗/MBTI/**面相手相**/合盘/解梦。亮点：① 先确定性排盘、再交给 AI 解读（支持导出命理体系文本给 AI）；② 提供公共 **MCP Server**；③ 历史记录 + 知识库 + @提及；④ AI 人设个性化；⑤ Web + iOS/Android 多端 |
| [dreamhunter2333/chatgpt-tarot-divination](https://github.com/dreamhunter2333/chatgpt-tarot-divination) | 907 | Vite + TS，纯 LLM | **星标最高的 AI 算命应用**。塔罗/八字/姓名五格/周公解梦/起名/梅花/姻缘。特色：流式打字机输出、每类自动存最近 10 条历史、响应式 + 暗色模式、Vercel 一键部署 + Docker + EXE 三种分发方式 |
| [Horace-Maxwell/Horosa-Web-App](https://github.com/Horace-Maxwell/Horosa-Web-App-comprehensively-improved-MacOS) | 368 | JavaScript | 玄学术数工作站：命/卜/工具三区 26 门主技法、60+ 子技法流派（占星/八字/紫微/七政/六壬/奇门/铁板神数等），含 20+ 推运技法 |
| [Gochatsurtsumia/mystic-oracle](https://github.com/Gochatsurtsumia/mystic-oracle) | 30 | React + Node + Groq Llama 3 | AI 算命 web app，用免费 Groq 推理降低成本 |
| [wych1987/tianjiyao-ai-fortune（天机瑶）](https://github.com/wych1987/tianjiyao-ai-fortune) | 20 | — | 商业化 AI 命理平台（tianjiyao.com）开源版 |
| [Rexingleung/mastra-fortune-telling](https://github.com/Rexingleung/mastra-fortune-telling) | 16 | TypeScript / Mastra 框架 | AI 算命网站：塔罗/风水/星座/谷子文化/起名 |
| [qilaidev/bazi-master](https://github.com/qilaidev/bazi-master) | 22 | React / Express / Postgres | 全球占卜 web app：八字/塔罗/易经/星座 + AI |
| [RoxyAPI/astrology-ai-chatbot](https://github.com/RoxyAPI/astrology-ai-chatbot) | 10 | TypeScript | 开源 AI 占星聊天机器人：印度占星/西式盘/八字/风水/塔罗/数字命理/解梦 |
| [yueying526/scent-soul](https://github.com/yueying526/scent-soul) | 0 | TypeScript | **创意跨界案例**：AI 面相 → 个性化香水推荐，"玄学 + 电商"变现场景 |

## 三、排盘引擎与基础设施（可直接复用的轮子）

| 项目 | Star | 说明 |
|---|---|---|
| [6tail/lunar-javascript](https://github.com/6tail/lunar-javascript) | 1667 | 农历/干支/八字/节气/宜忌/神煞全家桶，另有 lunar-python、lunar-java 等多语言版本（**Java 版可直接进我们 Spring Boot 后端**） |
| [SylarLong/iztro](https://github.com/SylarLong/iztro) | 4139 | 最流行的紫微斗数排盘 kit（TS），有 iztro-py Python 版 |
| [Renhuai123/ziwei-doushu](https://github.com/Renhuai123/ziwei-doushu) | 3932 | 紫微排盘引擎，基于倪海厦《天纪》体系，含四化系统、格局知识库、**古籍原文数据** |
| [jinchenma94/bazi-skill](https://github.com/jinchenma94/bazi-skill) | 2972 | 四柱八字命理分析 skill |
| [DestinyLinker/MingLi-Bench](https://github.com/DestinyLinker/MingLi-Bench) | 2377 | **LLM 命理能力评测基准**（八字 + 紫微），可用于量化对比各家 AI 解读质量 |
| [FANzR-arch/Numerologist_skills](https://github.com/FANzR-arch/Numerologist_skills) | 1143 | **给"赛博半仙"戴紧箍咒**：固定排盘步骤、减少 LLM 幻觉的工程框架（奇门/紫微） |
| [kentang2017/ichingshifa](https://github.com/kentang2017/ichingshifa) | 284 | Python 周易筮法/大衍之数/六爻/京房易 |
| [muyen/meihua-yishu](https://github.com/muyen/meihua-yishu) | 201 | 梅花易数 AI 占卜 skill（适配 Claude/ChatGPT/Gemini/DeepSeek） |
| [muyen/decoding-iching](https://github.com/muyen/decoding-iching) | 29 | 解码易经：用数据分析"逆向工程"易经，定位为决策工具 |
| [ai-freer/fortune-skill](https://github.com/ai-freer/fortune-skill) | 29 | Claude/Codex/Cursor 通用的八字 + 紫微 AI skill |
| [tommitoan/bazica](https://github.com/tommitoan/bazica) | 29 | Go 语言八字排盘库 |
| [songgoldenwind-crypto/liuyao-skills](https://github.com/songgoldenwind-crypto/liuyao-skills) | 28 | 全平台六爻 Agent Skill |
| [PINTO0309/hand-gesture-recognition-using-onnx](https://github.com/PINTO0309/hand-gesture-recognition-using-onnx) | 92 | ONNX 版手势/手部识别，替代 MediaPipe 全流程 |

## 四、别人怎么做的：三条技术路线

### 路线 1：传统 CV + 深度学习（yeonsumia/palmistry、Palm-Astro）
```
拍照 → MediaPipe 手部关键点 → 透视矫正/ROI 裁剪
     → 分割模型(U-Net/CNN) → 掌纹主线像素级分割
     → K-means/规则分类(生命线/头脑线/感情线/事业线)
     → 几何特征(长度/曲率/角度/交点) → 映射到命理结论
```
- 优点：可解释、可量化、输出稳定、不依赖大模型 API
- 缺点：需要标注数据集训练，掌纹分割数据稀缺（Palm-Astro 只做了 3 条主线）

### 路线 2：纯多模态 LLM（PALMYSTERY、kanxiang、dreamhunter2333）
```
掌心照片 → 直接喂给视觉 LLM + 相术知识提示词 → 结构化/流式解读
```
- 优点：开发成本极低、解读文本丰富有"人味"、产品差异化靠人设（如毒舌吐槽风）
- 缺点：幻觉严重、同一张图结论可能不一致、无客观量化指标

### 路线 3：确定性排盘 + LLM 解读（taibu 太卜、Numerologist_skills）★ 主流最佳实践
```
用户输入 → 确定性排盘引擎算出客观盘面(干支/星曜/卦象)
        → 盘面结构化文本 + 知识库(RAG) → LLM 生成解读
        → 固定排盘步骤、盘面不由 LLM 计算 → 幻觉可控
```
- 优点：计算部分 100% 可靠，LLM 只负责"翻译"成人话，幻觉面最小
- 太卜还把这套能力封装成 MCP Server，让任意 AI Agent 可调用

## 五、对我们项目（Palmistry Lab）的启示

1. **手相解析可以两条腿走路**：
   - 短期：借鉴 PALMYSTERY/kanxiang —— 掌心图 + 相术知识提示词（可参考 kanxiang 的《麻衣神相》知识库结构）直接调多模态 LLM，成本最低，快速上线；
   - 中期：借鉴 yeonsumia/palmistry —— 加 MediaPipe/ONNX 手部关键点做 ROI 矫正和客观测量（掌型、指长比、主线走向），把量化特征注入 prompt，提升稳定性和可信度。

2. **解读生成层对齐主流最佳实践**：不要让 LLM"看图编"，而是先由确定性模块输出结构化特征（如：生命线长/深/清晰、掌型为土形掌），再让 LLM 结合相术知识库生成文案。

3. **值得抄的产品功能点**（来自 taibu / dreamhunter2333）：
   - 流式打字机输出解读结果；
   - 每次分析自动存历史记录（最近 N 条）；
   - AI 人设可配置（专业命理师 / 毒舌吐槽 / 温暖治愈等风格）；
   - 分享海报/报告导出（kanxiang 的"撕纸海报"是很好的传播创意）；
   - 免责声明与娱乐定位声明（几乎所有项目都标注"仅供文化参考和娱乐"）。

4. **可直接引入的依赖**：
   - `lunar-java`（6tail）—— 农历/干支，Java 生态直接可用；
   - `mediapipe-hand-landmark-onnx` / `palm_roi` —— 手部关键点与掌心 ROI 提取；
   - kanxiang / taibu 的相术知识库 —— 可整理成我们后端的解读规则库。

5. **合规提醒**：国内对"算命"类产品有内容监管风险，成熟项目普遍定位为"传统文化 + 娱乐"，避免涉医疗/投资建议，建议我们沿用该定位。
