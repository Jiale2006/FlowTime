# -*- coding: utf-8 -*-
"""生成 web/index.html —— 工业风画廊页（内联 16 张 SVG）。"""
import os
import io
import re

HERE = os.path.dirname(os.path.abspath(__file__))
SVG_DIR = os.path.join(HERE, "svg")

GROUPS = [
    ("01", "Goal System", "目标体系", ["01_目标"]),
    ("02", "Review & Reflect", "复盘反省", ["02_复盘", "03_反省"]),
    ("03", "Physical", "运动健康", ["04_运动"]),
    ("04", "Reading", "阅读", ["05_阅读"]),
    ("05", "English", "英语（3 细项）", ["06_英语记单词", "07_英语听力", "08_英语阅读"]),
    ("06", "Mathematics", "数学（2 细项）", ["09_数学听课", "10_数学做题"]),
    ("07", "Major Courses", "专业课（4 门）",
     ["11_计算机网络", "12_计算机组成原理", "13_操作系统", "14_数据结构"]),
    ("08", "Civics & Study", "政治学习", ["15_政治", "16_学习"]),
    ("09", "Engineering", "编程", ["17_编程"]),
    ("10", "Life", "生活类", ["18_逛街", "19_看电影", "20_吃饭", "21_爬山", "22_散步"]),
]

TICKER = ["目标 · GOAL", "复盘 · REVIEW", "反省 · REFLECT", "运动 · SPORT",
          "阅读 · READ", "记单词 · VOCAB", "听力 · LISTEN", "阅读 · READING",
          "听课 · LECTURE", "做题 · PRACTICE", "计算机网络 · NETWORK",
          "组成原理 · ARCH", "操作系统 · OS", "数据结构 · DS",
          "政治 · POLITICS", "学习 · STUDY", "编程 · CODE", "生活 · LIFE"]


def strip_svg(path):
    s = io.open(path, encoding="utf-8").read()
    # 去掉 XML 声明（留内联），统一响应式尺寸
    s = re.sub(r"<\?xml[^>]*\?>", "", s)
    s = re.sub(r'width="\d+"\s+height="\d+"', 'width="100%" height="auto"', s, count=1)
    return s


def main():
    out = []
    out.append('<!doctype html>\n<html lang="zh-CN">\n<head>')
    out.append('<meta charset="utf-8">')
    out.append('<meta name="viewport" content="width=device-width, initial-scale=1">')
    out.append("<title>FLOWTIME · METRICS — 主题仪表卡</title>")
    out.append('<meta name="description" content="FlowTime 16 张主题仪表卡 · 工业实用视觉方向">')
    out.append('<link rel="preconnect" href="https://fonts.googleapis.com">')
    out.append('<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>')
    out.append('<link rel="stylesheet" href="https://fonts.googleapis.com/css2'
               '?family=Space+Mono:ital,wght@0,400;0,700;1,400'
               '&family=IBM+Plex+Sans:wght@400;500;600&display=swap">')
    out.append('<link rel="stylesheet" href="styles/tokens.css">')
    out.append('<link rel="stylesheet" href="styles/base.css">')
    out.append('<link rel="stylesheet" href="styles/components.css">')
    # 早于首帧标记 JS 可用 —— 用于「入场动画」的渐进增强闸门
    out.append("<script>document.documentElement.classList.add('js')</script>")
    out.append("</head>\n<body>")
    out.append('<div class="scanlines" aria-hidden="true"></div>')

    # ── 版头 ──
    out.append('<header class="masthead">')
    out.append('  <div class="reveal">')
    out.append('    <p class="eyebrow">FlowTime Metrics · v1.0 · Industrial</p>')
    out.append('    <h1>主题<span class="dim">仪表卡</span></h1>')
    out.append('    <p class="lede">22 个细项的量化视图 —— 发丝网格、'
               '等宽读数、单色域编码。每张卡都是一个可直接嵌入的独立 SVG。</p>')
    out.append("  </div>")
    out.append('  <aside class="readout reveal" aria-label="总览读数">')
    out.append('    <div class="row"><span class="k">Cards</span>'
               '<span class="v acc">22</span></div>')
    out.append('    <div class="row"><span class="k">Groups</span>'
               '<span class="v">10</span></div>')
    out.append('    <div class="row"><span class="k">Format</span>'
               '<span class="v">SVG</span></div>')
    out.append('    <div class="row"><span class="k">Deps</span>'
               '<span class="v">0</span></div>')
    out.append("  </aside>")
    out.append("</header>")

    # ── 跑马灯 ──
    items = "".join('<span class="ticker__item">%s</span>' % t for t in TICKER)
    out.append('<div class="ticker" aria-hidden="true">')
    out.append('  <div class="ticker__track">%s</div>' % items)
    out.append('  <div class="ticker__track">%s</div>' % items)
    out.append("</div>")

    # ── 画廊 ──
    out.append('<main class="gallery">')
    for idx, en, cn, files in GROUPS:
        out.append('<section aria-labelledby="sec-%s">' % idx)
        out.append('  <div class="section-rule">')
        out.append('    <span class="idx">%s</span>' % idx)
        out.append('    <h2 class="nm" id="sec-%s">%s</h2>' % (idx, en))
        out.append('    <span class="ct">%s · %02d</span>' % (cn, len(files)))
        out.append("  </div>")
        out.append('  <div class="grid">')
        for f in files:
            p = os.path.join(SVG_DIR, f + ".svg")
            if not os.path.exists(p):
                continue
            code = f.split("_", 1)[1]
            out.append('    <a class="tile reveal" href="svg/%s.svg" target="_blank" '
                       'rel="noopener" aria-label="打开 %s 原始 SVG">' % (f, code))
            out.append(strip_svg(p))
            out.append('      <span class="tile__tag">%s · SVG</span>' % code)
            out.append("    </a>")
        out.append("  </div>")
        out.append("</section>")
    out.append("</main>")

    # ── 页脚 ──
    out.append('<footer class="colophon">')
    out.append("  <span>FlowTime Metrics · Industrial Utility</span>")
    out.append('  <span><span class="kbd">Tab</span> 遍历 · '
               '<span class="kbd">Click</span> 打开原图</span>')
    out.append("  <span>暖近黑 #141210 · 琥珀 #ff8a3d</span>")
    out.append("</footer>")

    out.append('<script src="scripts/main.js"></script>')
    out.append("</body>\n</html>")

    dst = os.path.join(HERE, "web", "index.html")
    io.open(dst, "w", encoding="utf-8").write("\n".join(out))
    n = sum(len(f) for _, _, _, f in GROUPS)
    print("画廊页 → %s（%d 张卡）" % (dst, n))


if __name__ == "__main__":
    main()
