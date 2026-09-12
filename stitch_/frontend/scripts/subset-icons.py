#!/usr/bin/env python3
"""把 Material Symbols 可变字体裁剪到项目实际用到的图标。

两步：
1. 剪掉 GSUB 里用不到的合字规则（否则 fonttools 的闭包会保留全部 3800+ 图标）；
2. fontTools.subset 按文本（图标名 → 保留字母与可达合字）子集化。

新增图标后：把图标名加进 ICONS 重跑本脚本即可。
用法：python3 scripts/subset-icons.py
"""
from fontTools.ttLib import TTFont
from fontTools import subset
import os

ICONS = [
    'account_circle', 'arrow_back', 'aspect_ratio', 'auto_awesome', 'auto_fix_high',
    'auto_stories', 'cached', 'calendar_month', 'chevron_right', 'cloud_off',
    'favorite', 'flare', 'flash_on', 'pan_tool_alt', 'psychology',
    'share', 'smart_display', 'star', 'stars', 'temple_hindu', 'timeline'
]

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'node_modules/material-symbols/material-symbols-outlined.woff2')
PRUNED = '/tmp/material-symbols-pruned.woff2'
OUT = os.path.join(ROOT, 'src/assets/fonts/material-symbols-subset.woff2')

font = TTFont(SRC)
keep = set(ICONS)
gsub = font['GSUB'].table
for lookup in gsub.LookupList.Lookup:
    tables = []
    for st in lookup.SubTable:
        if lookup.LookupType == 7:
            tables.append(st.ExtSubTable)
        else:
            tables.append(st)
    for inner in tables:
        if inner.LookupType == 4:
            new_ligatures = {}
            for first, ligs in inner.ligatures.items():
                kept = [lig for lig in ligs if lig.LigGlyph in keep]
                if kept:
                    new_ligatures[first] = kept
            inner.ligatures = new_ligatures
font.save(PRUNED)

options = subset.Options()
options.layout_features = ['*']
options.flavor = 'woff2'
subsetter = subset.Subsetter(options=options)
subsetter.populate(text=' '.join(ICONS))
subsetter.subset(font)
font.save(OUT)

out_size = os.path.getsize(OUT)
print(f'subset saved: {OUT} ({out_size / 1024:.1f} KB, {len(font.getGlyphOrder())} glyphs)')
