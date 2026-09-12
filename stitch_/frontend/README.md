# 手相研究所前端（Vite 多页应用）

单一源码工程。构建产物输出到 `../backend/src/main/resources/static/`（gitignore，不入库），由 Spring Boot 直接托管。

## 常用命令

```bash
npm install          # 安装依赖
npm run dev          # 开发服务器（5173 端口，/api 代理到 8080，支持 HMR）
npm run build        # 构建到 backend 静态资源目录（mvn package/verify 也会自动执行）
```

纯后端迭代时跳过前端构建：

```bash
mvn test -Dskip.installnodetools -Dskip.npm
```

## 结构

```
frontend/
├── *.html                 # 多页入口（页面 URL 即 /capture.html 这样的根路径）
├── src/
│   ├── pages/<page>/main.js   # 各页逻辑（ES Module）
│   ├── shared/js/         # 共享模块
│   │   ├── api.js         # API 客户端：统一 BASE/超时/错误归一化/SSE 解析/身份/埋点
│   │   ├── store.js       # localStorage 唯一入口（key 契约 + 生命周期清理）
│   │   ├── validate.js    # 手掌照片本地校验 + 压缩
│   │   └── shareCard.js   # 分享卡公共绘制
│   ├── styles/main.css    # 字体 + Tailwind + 壳层样式
│   └── assets/            # 本地图片/字体资源
├── tailwind.config.js     # 全站唯一 token 配置（DESIGN_TOKENS.md 为设计源）
└── scripts/
    └── subset-icons.py    # Material Symbols 字体子集化（3.4MB → 24KB）
```

## 页面清单

| URL | 职责 |
|---|---|
| /index.html | 演示导航 hub |
| /home.html | 产品首页引导 |
| /capture.html | 拍照/上传 + 本地手掌校验 + 压缩 |
| /scanning.html | 3-5 秒纯动效过渡页 |
| /trace.html | 掌纹描摹 + SSE 流式分析（单人/周记录双流程） |
| /report.html | 单人报告 + 解锁 + 稀有印记私域弹层 + 分享卡 |
| /cp.html | CP 合拍报告 |
| /weekly.html | 周记录 |
| /calendar.html | 手相日历 |
| /record-detail.html | 当日回顾 |
| /monthly.html | 月度能量月相图 |
| /admin_metrics.html | 运营看板（受访问令牌保护） |

## 图标字体子集化

图标使用 Material Symbols 子集（当前 21 个图标，24KB）。新增图标后：
在 `scripts/subset-icons.py` 的 `ICONS` 列表补充名字，执行 `python3 scripts/subset-icons.py`（需要 `pip install fonttools brotli`）。
