// 一次性迁移脚本：把 variants/<old>/code.html 机械变换为 frontend/<new>.html
// 手工部分（main.js 模块化）在脚本外单独完成。用完即删。
import { readFileSync, writeFileSync } from 'node:fs';

const LINK_MAP = [
  ['../light_1/code.html', '/home.html'],
  ['./index.html', '/index.html'],
  ['../index.html', '/index.html'],
  ['../light_2/code.html', '/capture.html'],
  ['../ai..._light/code.html', '/scanning.html'],
  ['../trace_confirm/code.html', '/trace.html'],
  ['../light_3/code.html', '/report.html'],
  ['../cp_light/code.html', '/cp.html'],
  ['../weekly_quick/code.html', '/weekly.html'],
  ['../palm_calendar/code.html', '/calendar.html'],
  ['../record_detail/code.html', '/record-detail.html'],
  ['../monthly_energy/code.html', '/monthly.html']
];

// 热链图（googleusercontent 已不可达）→ 本地占位资源
const IMG_MAP = [
  [/https:\/\/lh3\.googleusercontent\.com\/aida-public\/AB6AXuB-N4jb[^"]*/, '/src/assets/img/palm-hero.svg'],
  [/https:\/\/lh3\.googleusercontent\.com\/aida-public\/AB6AXuCkU3D_[^"]*/, '/src/assets/img/feature-orb.svg'],
  [/https:\/\/lh3\.googleusercontent\.com\/aida-public\/AB6AXuC7LIU[^"]*/, '/src/assets/img/feature-wheel.svg'],
  [/https:\/\/lh3\.googleusercontent\.com\/aida-public\/AB6AXuBIkKuul[^"]*/, '/src/assets/img/palm-hero.svg'],
  [/https:\/\/lh3\.googleusercontent\.com\/aida-public\/AB6AXuDgxR_[^"]*/, '/src/assets/img/palm-hero.svg'],
  [/https:\/\/lh3\.googleusercontent\.com\/aida-public\/AB6AXuBJfMSY[^"]*/, '/src/assets/img/feature-orb.svg'],
  [/https:\/\/lh3\.googleusercontent\.com\/aida-public\/AB6AXuAritN[^"]*/, '/src/assets/img/feature-wheel.svg'],
  [/https:\/\/lh3\.googleusercontent\.com\/aida-public\/[^"]*/, '/src/assets/img/palm-hero.svg']
];

function migrate(oldPath, newName, moduleName) {
  let html = readFileSync(oldPath, 'utf8');

  // 1) 去掉 Tailwind Play CDN 与内联 tailwind.config
  html = html.replace(/\s*<script src="\.\.\/vendor\/tailwind-play\.js"><\/script>\n?/g, '\n');
  html = html.replace(/[ \t]*<script id="tailwind-config">[\s\S]*?<\/script>\n?/g, '');
  // 2) 去掉第三方字体 link（字体已由 npm 包本地化）
  html = html.replace(/[ \t]*<link[^>]*fonts\.loli\.net[^>]*>\n?/g, '');
  // 3) 链接重命名
  for (const [from, to] of LINK_MAP) {
    html = html.split(from).join(to);
  }
  // 4) 热链图 → 本地资源
  for (const [re, to] of IMG_MAP) {
    html = html.replace(re, to);
  }
  // 5) 移除原内联业务脚本（main.js 单独重写）
  html = html.replace(/\n?[ \t]*<script>\n[\s\S]*?<\/script>\n?/g, '\n');
  // 6) 挂载模块入口
  html = html.replace('</body>', `  <script type="module" src="/src/pages/${moduleName}/main.js"></script>\n</body>`);

  writeFileSync(newName, html);
  console.log('migrated ->', newName);
}

const [oldPath, newName, moduleName] = process.argv.slice(2);
migrate(oldPath, newName, moduleName);
