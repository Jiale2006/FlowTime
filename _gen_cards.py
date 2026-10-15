# -*- coding: utf-8 -*-
"""FlowTime 仪表卡 SVG 生成器 —— 工业实用方向（Industrial / Utilitarian）。

设计约束：
- 暖近黑底 + 发丝边框 + 四角括号 + 等宽数字 + 状态色体系
- 纯自包含：内联样式、系统字体族、无外部依赖、无脚本
- 单色域纪律：每卡一个状态色（数据编码，非装饰），强调色统一为琥珀
"""
import os
import math

# ── 令牌（与 web/styles/tokens.css 保持一致）──
BG = "#141210"          # 页面近黑
CARD = "#1c1917"        # 卡片表面
CARD2 = "#242019"       # 次级表面
INK = "#f4efe7"         # 主文字（暖近白）
MUTED = "#8b8375"       # 次级文字
HAIR = "rgba(244,239,231,0.13)"   # 发丝线
HAIR2 = "rgba(244,239,231,0.07)"
ACCENT = "#ff8a3d"      # 单一强调色：琥珀

MONO = "'SF Mono','JetBrains Mono','Cascadia Mono',Consolas,ui-monospace,monospace"
SANS = "'PingFang SC','Microsoft YaHei','Hiragino Sans GB','Noto Sans SC',sans-serif"

W, H = 400, 428
PAD = 26


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


# ── 基础元件 ──────────────────────────────────────────────
def corner_brackets(x, y, w, h, s, color, sw=1.6, op=1.0):
    """四角括号 —— 工业仪表标志性装饰。
    每角画一个 L 形：从边的延长方向折回。"""
    out = []
    # (角点, 水平方向, 垂直方向)
    for cx, cy, dx, dy in ((x, y, 1, 1), (x + w, y, -1, 1),
                           (x, y + h, 1, -1), (x + w, y + h, -1, -1)):
        out.append(
            '<path d="M%.1f %.1f h%.1f M%.1f %.1f v%.1f" fill="none" stroke="%s" '
            'stroke-width="%s" opacity="%s"/>'
            % (cx, cy, dx * s, cx, cy, dy * s, color, sw, op)
        )
    return "".join(out)


def hairline(x1, y, x2, color=HAIR, sw=1):
    return '<line x1="%s" y1="%s" x2="%s" y2="%s" stroke="%s" stroke-width="%s"/>' % (
        x1, y, x2, y, color, sw)


def seg(x1, y1, x2, y2, color=HAIR2, sw=1, dash=None):
    """任意方向发丝线"""
    d = ' stroke-dasharray="%s"' % dash if dash else ""
    return ('<line x1="%.1f" y1="%.1f" x2="%.1f" y2="%.1f" stroke="%s" stroke-width="%s"%s/>'
            % (x1, y1, x2, y2, color, sw, d))


def grid_bg(x, y, w, h, step=16, color=HAIR2):
    """低密度网格底纹 —— 氛围层"""
    out = ['<clipPath id="clip%d_%d"><rect x="%s" y="%s" width="%s" height="%s" rx="8"/>'
           '</clipPath>' % (int(x), int(y), x, y, w, h)]
    out.append('<g clip-path="url(#clip%d_%d)">' % (int(x), int(y)))
    gx = x
    while gx < x + w:
        out.append(seg(gx, y, gx, y + h, color, 1))
        gx += step
    gy = y
    while gy < y + h:
        out.append(seg(x, gy, x + w, gy, color, 1))
        gy += step
    out.append("</g>")
    return "".join(out)


def label(x, y, text, color=MUTED, size=10, weight="500", mono=False):
    return ('<text x="%s" y="%s" font-size="%s" font-weight="%s" fill="%s" '
            'font-family="%s" letter-spacing="0.06em">%s</text>'
            % (x, y, size, weight, color, MONO if mono else SANS, esc(text)))


def numeral(x, y, text, color=INK, size=30, anchor="start"):
    """等宽数据读数"""
    return ('<text x="%s" y="%s" font-size="%s" font-weight="600" fill="%s" '
            'font-family="%s" text-anchor="%s" '
            'style="font-variant-numeric:tabular-nums">%s</text>'
            % (x, y, size, color, MONO, anchor, esc(text)))


# ── 图表元件 ──────────────────────────────────────────────
def gauge(cx, cy, r, pct, color, track=HAIR, sw=9, read=None, unit=""):
    """环形仪表（开口环）—— 270° 刻度盘"""
    circ = 2 * math.pi * r
    span = 0.75                 # 270°
    dash = circ * span * pct
    gap = circ - dash
    rot = 135                   # 起点左下
    out = [
        '<circle cx="%s" cy="%s" r="%s" fill="none" stroke="%s" stroke-width="%s" '
        'stroke-linecap="butt" stroke-dasharray="%.2f %.2f" '
        'transform="rotate(%s %s %s)"/>' % (cx, cy, r, track, sw, circ * span, circ, rot, cx, cy),
        '<circle cx="%s" cy="%s" r="%s" fill="none" stroke="%s" stroke-width="%s" '
        'stroke-linecap="butt" stroke-dasharray="%.2f %.2f" '
        'transform="rotate(%s %s %s)"/>' % (cx, cy, r, color, sw, dash, gap, rot, cx, cy),
    ]
    # 刻度
    for i in range(11):
        a = math.radians(135 + 270 * i / 10)
        r1, r2 = r + sw / 2 + 3, r + sw / 2 + (6 if i % 5 == 0 else 4)
        out.append(
            '<line x1="%.2f" y1="%.2f" x2="%.2f" y2="%.2f" stroke="%s" stroke-width="%s"/>'
            % (cx + math.cos(a) * r1, cy + math.sin(a) * r1,
               cx + math.cos(a) * r2, cy + math.sin(a) * r2,
               HAIR, 1.4 if i % 5 == 0 else 1))
    if read is not None:
        out.append(numeral(cx, cy + 7, read, color, 30, "middle"))
    if unit:
        out.append(label(cx, cy + 24, unit, MUTED, 9.5, "500", True).replace(
            'letter-spacing="0.06em"', 'letter-spacing="0.14em" text-anchor="middle"'))
    return "".join(out)


def histogram(x, y, w, h, values, color, labels=None, maxv=None, ticks=3):
    """柱状直方图 + 基准刻度线"""
    n = len(values)
    maxv = maxv or max(values)
    out = []
    for t in range(ticks + 1):
        gy = y + h - h * t / ticks
        out.append(hairline(x, gy, x + w, HAIR2, 1))
    bw = w / n
    # 柱宽占比可调（默认 0.56；4 根柱时收窄更协调）
    bw_in = bw * (0.42 if n <= 4 else 0.56)
    for i, v in enumerate(values):
        bh = h * (v / maxv)
        bx = x + i * bw + (bw - bw_in) / 2
        by = y + h - bh
        # 最近一根用强调色，其余用状态色
        col = ACCENT if i == n - 1 else color
        out.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" fill="%s"/>'
                   % (bx, by, bw_in, bh, col))
        out.append('<rect x="%.1f" y="%s" width="%.1f" height="1.6" fill="%s" opacity="0.9"/>'
                   % (bx, by, bw_in, INK))
    if labels:
        for i, lb in enumerate(labels):
            out.append(label(x + i * bw + bw / 2, y + h + 14, lb, MUTED, 8.5, "500", True)
                       .replace('letter-spacing="0.06em"',
                                'letter-spacing="0" text-anchor="middle"'))
    return "".join(out)


def sparkline(x, y, w, h, values, color, labels=None, maxv=None):
    """折线 + 面积 + 端点标记"""
    n = len(values)
    maxv = maxv or max(values)
    pts = [(x + w / (n - 1) * i, y + h - h * (v / maxv)) for i, v in enumerate(values)]
    d = "M" + " L".join("%.1f %.1f" % p for p in pts)
    fa = d + " L%.1f %.1f L%.1f %.1f Z" % (pts[-1][0], y + h, pts[0][0], y + h)
    out = [
        '<defs><linearGradient id="sp%d%d" x1="0" y1="0" x2="0" y2="1">'
        '<stop offset="0" stop-color="%s" stop-opacity="0.30"/>'
        '<stop offset="1" stop-color="%s" stop-opacity="0"/></linearGradient></defs>'
        % (int(x), int(y), color, color),
        '<path d="%s" fill="url(#sp%d%d)"/>' % (fa, int(x), int(y)),
        '<path d="%s" fill="none" stroke="%s" stroke-width="2" '
        'stroke-linejoin="miter" stroke-linecap="square"/>' % (d, color),
    ]
    for i, (px, py) in enumerate(pts):
        r = 3.2 if i == n - 1 else 2.2
        out.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" fill="%s"/>'
                   % (px - r, py - r, r * 2, r * 2, ACCENT if i == n - 1 else color))
    if labels:
        for i, lb in enumerate(labels):
            out.append(label(pts[i][0], y + h + 14, lb, MUTED, 8.5, "500", True)
                       .replace('letter-spacing="0.06em"',
                                'letter-spacing="0" text-anchor="middle"'))
    return "".join(out)


def radar(cx, cy, r, values, color, axes, maxv=5):
    """雷达图 —— 工业风用极细网格 + 直角数据点"""
    n = len(axes)
    out = []
    for lv in (0.25, 0.5, 0.75, 1.0):
        pts = []
        for i in range(n):
            a = -math.pi / 2 + 2 * math.pi * i / n
            pts.append("%.1f,%.1f" % (cx + math.cos(a) * r * lv, cy + math.sin(a) * r * lv))
        out.append('<polygon points="%s" fill="none" stroke="%s" stroke-width="1" '
                   'stroke-dasharray="%s"/>' % (" ".join(pts), HAIR2, "3 3" if lv < 1 else "0"))
    for i in range(n):
        a = -math.pi / 2 + 2 * math.pi * i / n
        out.append(seg(cx, cy, cx + math.cos(a) * r, cy + math.sin(a) * r, HAIR2, 1))
    pts = []
    for i, v in enumerate(values):
        a = -math.pi / 2 + 2 * math.pi * i / n
        rr = r * (v / maxv)
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    out.append('<polygon points="%s" fill="%s" fill-opacity="0.18" stroke="%s" '
               'stroke-width="1.8"/>' % (" ".join("%.1f,%.1f" % p for p in pts), color, color))
    for px, py in pts:
        out.append('<rect x="%.1f" y="%.1f" width="4.4" height="4.4" fill="%s"/>'
                   % (px - 2.2, py - 2.2, ACCENT))
    for i, lb in enumerate(axes):
        a = -math.pi / 2 + 2 * math.pi * i / n
        lx = cx + math.cos(a) * (r + 19)
        ly = cy + math.sin(a) * (r + 19) + 3.5
        out.append(label(lx, ly, lb, MUTED, 9.5, "500")
                   .replace('letter-spacing="0.06em"',
                            'letter-spacing="0.04em" text-anchor="middle"'))
    return "".join(out)


def meter_rows(x, y, w, rows, color):
    """发丝分割的横条读数行 rows=[(名, 值, pct, 色)]"""
    out = []
    rh = 30
    for i, (nm, val, pct, col) in enumerate(rows):
        ry = y + i * rh
        out.append(hairline(x, ry - 8, x + w, HAIR2, 1))
        out.append(label(x, ry + 3, nm, MUTED, 10))
        out.append(numeral(x + w, ry + 4, val, col, 13, "end"))
        # 轨道 + 填充（发丝风格：细条）
        out.append('<rect x="%s" y="%.1f" width="%.1f" height="3" fill="%s"/>'
                   % (x, ry + 10, w, HAIR))
        out.append('<rect x="%s" y="%.1f" width="%.1f" height="3" fill="%s"/>'
                   % (x, ry + 10, max(3, w * pct), col))
    out.append(hairline(x, y + len(rows) * rh - 8, x + w, HAIR2, 1))
    return "".join(out)


# ── 图标（工业线性，几何硬朗）─────────────────────────────
def ic(fn, x, y, s, color):
    return '<g transform="translate(%s,%s) scale(%s)" fill="none" stroke="%s" ' \
           'stroke-width="1.7" stroke-linecap="square">%s</g>' % (x, y, s, color, fn)


ICONS = {
    "target": '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="4.6"/>'
              '<rect x="11" y="11" width="2" height="2" fill="CUR" stroke="none"/>'
              '<path d="M12 12 L21 3 M21 3 h-5 M21 3 v5"/>',
    "review": '<path d="M20 12 A8 8 0 1 1 12 4"/><path d="M12 4 L8.6 1.4 M12 4 L13.6 7.6"/>'
              '<path d="M12 8.6 V12.4 L15 13.6"/>',
    "run": '<path d="M17 5.2 L13.6 9.4 L16.4 12.2 L17.2 17.4"/>'
           '<path d="M13.6 9.4 L8 10.2 L6.6 14"/><path d="M16.4 12.2 L21 14"/>'
           '<circle cx="17" cy="4" r="2"/>',
    "dumbbell": '<rect x="1.6" y="9" width="3" height="6"/><rect x="4.6" y="6.6" width="3.4" height="10.8"/>'
                '<rect x="16" y="6.6" width="3.4" height="10.8"/><rect x="19.4" y="9" width="3" height="6"/>'
                '<path d="M8 12 H16"/>',
    "book": '<path d="M2 4.6 H11 V19.4 H2 Z"/><path d="M13 4.6 H22 V19.4 H13 Z"/>'
            '<path d="M12 4.6 V19.4"/>',
    "english": '<rect x="1.6" y="5" width="20.8" height="14"/>'
               '<rect x="4.4" y="7.8" width="6.6" height="8.4"/>'
               '<path d="M14 9.4 h5.6 M14 12 h5.6 M14 14.6 h3.4"/>',
    "sigma": '<path d="M5 3 H19 L12 12 L19 21 H5"/>',
    "chip": '<rect x="4.6" y="4.6" width="14.8" height="14.8"/>'
            '<rect x="9" y="9" width="6" height="6"/>'
            '<path d="M8 4.6 V1.4 M12 4.6 V1.4 M16 4.6 V1.4 M8 19.4 V22.6 M12 19.4 V22.6 '
            'M16 19.4 V22.6 M4.6 8 H1.4 M4.6 12 H1.4 M4.6 16 H1.4 M19.4 8 H22.6 '
            'M19.4 12 H22.6 M19.4 16 H22.6"/>',
    "politics": '<path d="M2 7.6 L12 2.6 L22 7.6 Z"/><path d="M5 7.6 V17 M10 7.6 V17 '
                'M14 7.6 V17 M19 7.6 V17"/><path d="M2 19.4 H22"/>',
    "study": '<path d="M12 2.6 L23 8 L12 13.4 L1 8 Z"/><path d="M5.6 10.8 V16 '
             'Q12 20.4 18.4 16 V10.8"/><path d="M23 8 V14.6"/>',
    "reflect": '<path d="M12 2.8 A9.2 9.2 0 0 1 12 21.2 Z"/>'
               '<circle cx="12" cy="12" r="3"/><path d="M12 2.8 V21.2"/>',
    "code": '<path d="M8 7.6 L2.6 12 L8 16.4"/><path d="M16 7.6 L21.4 12 L16 16.4"/>'
            '<path d="M14 4.6 L10 19.4"/>',
    "shop": '<path d="M3.4 7.2 H20.6 L19 21 H5 Z"/><path d="M8 7.2 V4.4 A4 4 0 0 1 16 4.4 V7.2"/>',
    "movie": '<rect x="1.6" y="3.6" width="20.8" height="16.8"/>'
             '<path d="M1.6 7.6 H22.4 M7 3.6 V7.6 M17 3.6 V7.6"/>'
             '<path d="M10.2 10.6 L15.4 12 L10.2 13.4 Z" fill="CUR" stroke="none"/>',
    "food": '<path d="M2 13.4 A10 10 0 0 1 22 13.4 Z"/><path d="M2 13.4 H22"/>'
            '<path d="M2 17 H22"/><path d="M19 2.6 V21.4"/>',
    "hike": '<path d="M1.4 20.4 L8.4 7 L13 14 L16.8 7.6 L22.6 20.4 Z"/>'
            '<path d="M8.4 7 L6.6 10.4 H10.2 Z" fill="CUR" stroke="none"/>',
    "walk": '<circle cx="13" cy="4.2" r="2.4"/>'
            '<path d="M12 8.4 L9.6 13 L12 15.6 L10.4 21.4"/>'
            '<path d="M13 13 L16.4 17.4 L15.6 21.4"/><path d="M9.6 9.4 L4 11.6 M13 9.4 L18.6 8.4"/>',
}


def icon(key, x, y, s, color):
    body = ICONS[key].replace("CUR", color)
    return ('<g transform="translate(%.1f,%.1f) scale(%.3f)" fill="none" stroke="%s" '
            'stroke-width="1.7" stroke-linecap="square" stroke-linejoin="miter">%s</g>'
            % (x, y, s / 24.0, color, body))


# ── 卡片装配 ──────────────────────────────────────────────
def card(theme, subtitle, code, color, icon_key, primary, blocks):
    """primary = (标题, 读数, 单位)；blocks = [svg...]"""
    p = [
        '<svg xmlns="http://www.w3.org/2000/svg" width="%s" height="%s" viewBox="0 0 %s %s">'
        % (W, H, W, H),
        "<defs>",
        '<linearGradient id="vig" x1="0" y1="0" x2="0" y2="1">'
        '<stop offset="0" stop-color="#ffffff" stop-opacity="0.045"/>'
        '<stop offset="0.55" stop-color="#ffffff" stop-opacity="0"/>'
        "</linearGradient>",
        "</defs>",
        # 卡面
        '<rect width="%s" height="%s" rx="10" fill="%s"/>' % (W, H, CARD),
        '<rect width="%s" height="%s" rx="10" fill="url(#vig)"/>' % (W, H),
        # 左侧状态色轨
        '<rect x="0" y="0" width="3" height="%s" rx="1.5" fill="%s"/>' % (H, color),
        # 网格氛围层
        grid_bg(0, 0, W, H, 22),
        # 顶部：图标 + 主题 + 代码
        icon(icon_key, PAD, 22, 22, color),
        '<text x="%s" y="%s" font-size="17" font-weight="600" fill="%s" '
        'font-family="%s" letter-spacing="0.02em">%s</text>'
        % (PAD + 32, 40, INK, SANS, esc(theme)),
        label(PAD, 58, subtitle, MUTED, 10.5),
        label(W - PAD, 30, code, color, 9.5, "600", True).replace(
            'letter-spacing="0.06em"', 'letter-spacing="0.14em" text-anchor="end"'),
        hairline(PAD, 74, W - PAD, HAIR, 1),
    ]
    # 主读数区（单位用 tspan 紧贴数字，避免位置估算）
    p.append(label(PAD, 96, primary[0], MUTED, 10))
    p.append(
        '<text x="%s" y="128" font-size="38" font-weight="600" fill="%s" '
        'font-family="%s" style="font-variant-numeric:tabular-nums">%s'
        '<tspan font-size="14" fill="%s" dx="4">%s</tspan></text>'
        % (PAD, INK, MONO, esc(primary[1]), MUTED, esc(primary[2])))
    # 状态色块（数据编码，非装饰）
    p.append('<rect x="%s" y="%s" width="%s" height="%s" fill="%s" opacity="0.13"/>'
             % (PAD, 100, W - PAD * 2, 34, color))
    p.append(hairline(PAD, 148, W - PAD, HAIR, 1))
    for b in blocks:
        p.append(b)
    # 底部：四角括号（只框图表区，避开首末行文字）+ 页码
    bx = PAD
    by = 164
    bw = W - PAD * 2
    bh = 214          # 164 → 378，留出底部 50px 给页脚
    p.append(corner_brackets(bx, by, bw, bh, 8, color, 1.5, 0.45))
    p.append(label(PAD, H - 16, "FLOWTIME · METRICS", MUTED, 9, "500", True))
    p.append(label(W - PAD, H - 16, "SOC", MUTED, 9, "500", True).replace(
        'letter-spacing="0.06em"', 'letter-spacing="0.14em" text-anchor="end"'))
    p.append("</svg>")
    return "\n".join(p)


def blk_section(y, title, color=MUTED):
    return label(PAD, y, title, color, 10, "600")


# ── 16 张卡数据 ───────────────────────────────────────────

# 28 张卡数据（保留脚本前 351 行公共部分，从本行起替换 CARDS 段）

CARDS = []


def C(file, theme, subtitle, code, color, icon, primary, blocks):
    CARDS.append(dict(file=file, theme=theme, subtitle=subtitle, code=code,
                      color=color, icon=icon, primary=primary, blocks=blocks))


# ══ 目标体系 ══════════════════════════════════════════════
C("01_目标", "目标", "拆解 · 追踪 · 达成", "GOAL", "#ff8a3d", "target",
  ("本季度综合达成率", "78", "%"), [
      blk_section(170, "周度达成 · 近 7 日"),
      gauge(84, 244, 40, 0.78, "#ff8a3d", read="78", unit="PCT"),
      histogram(146, 196, 228, 66, [40, 62, 55, 88, 70, 95, 82], "#ff8a3d",
                ["MO", "TU", "WE", "TH", "FR", "SA", "SU"], 100),
      label(146, 288, "末位为当前值", MUTED, 9),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("学习成长", "42h", 0.82, "#ff8a3d"),
          ("健康运动", "26h", 0.55, "#ff8a3d"),
          ("生活休闲", "15h", 0.34, "#8b8375"),
      ], "#ff8a3d")])

# ══ 复盘反省 ══════════════════════════════════════════════
C("02_复盘", "复盘", "回顾 · 归因 · 改进", "REVIEW", "#8f82f5", "review",
  ("本周复盘完成度", "92", "%"), [
      blk_section(170, "复盘维度自评"),
      radar(96, 246, 52, [4.5, 4.0, 3.4, 4.8, 3.9], "#8f82f5",
            ["计划", "执行", "专注", "结果", "改进"]),
      label(180, 196, "周度复盘次数", MUTED, 10),
      sparkline(180, 210, 194, 54, [3, 5, 4, 6, 7, 6], "#8f82f5",
                ["W1", "W2", "W3", "W4", "W5", "W6"]),
      numeral(180, 298, "5.2", "#8f82f5", 20),
      label(214, 298, "次 / 周 · 均值", MUTED, 10),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("时间安排", "92%", 0.92, "#8f82f5"),
          ("专注度", "85%", 0.85, "#8f82f5"),
          ("执行力", "70%", 0.70, "#8b8375"),
      ], "#8f82f5")])

C("03_反省", "反省", "觉察 · 修正 · 迭代", "REFLECT", "#9d74f8", "reflect",
  ("自省记录累计", "20", "条"), [
      blk_section(170, "自省维度评分"),
      radar(96, 246, 52, [3.8, 4.2, 3.2, 4.6, 3.6], "#9d74f8",
            ["情绪", "专注", "沟通", "行动", "心态"]),
      label(180, 196, "觉察频率 · 近 5 周", MUTED, 10),
      sparkline(180, 210, 194, 54, [2, 4, 3, 5, 6], "#9d74f8",
                ["9/7", "9/14", "9/21", "9/28", "10/5"]),
      numeral(180, 298, "6", "#9d74f8", 20),
      label(196, 298, "条 · 本周", MUTED, 10),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("习惯坚持", "82%", 0.82, "#9d74f8"),
          ("情绪管理", "76%", 0.76, "#9d74f8"),
          ("时间规划", "68%", 0.68, "#8b8375"),
      ], "#9d74f8")])

# ══ 运动健康 ══════════════════════════════════════════════
C("04_运动", "运动", "跑步 · 健身 · 达标", "SPORT", "#33c48d", "dumbbell",
  ("今日运动目标达成", "86", "%"), [
      blk_section(170, "周度时长 · 分钟"),
      gauge(84, 244, 40, 0.86, "#33c48d", read="86", unit="PCT"),
      histogram(146, 196, 228, 66, [35, 48, 30, 62, 55, 72, 45], "#33c48d",
                ["MO", "TU", "WE", "TH", "FR", "SA", "SU"], 80),
      label(146, 288, "峰值 72min · 均值 49.6", MUTED, 9),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("跑步", "38%", 0.76, "#33c48d"),
          ("力量训练", "35%", 0.70, "#33c48d"),
          ("拉伸放松", "17%", 0.34, "#8b8375"),
      ], "#33c48d")])

# ══ 阅读 ══════════════════════════════════════════════════
C("05_阅读", "阅读", "输入 · 思考 · 沉淀", "READ", "#3e8cf0", "book",
  ("本月阅读进度", "64", "%"), [
      blk_section(170, "月度页数 · 千页"),
      gauge(84, 244, 40, 0.64, "#3e8cf0", read="64", unit="PCT"),
      label(146, 196, "近 6 月阅读量", MUTED, 10),
      sparkline(146, 210, 228, 54, [120, 180, 150, 240, 210, 300], "#3e8cf0",
                ["MAY", "JUN", "JUL", "AUG", "SEP", "OCT"]),
      numeral(146, 298, "300", "#3e8cf0", 20),
      label(192, 298, "页 / 月 · 当前", MUTED, 10),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("技术书籍", "45%", 0.90, "#3e8cf0"),
          ("文学小说", "32%", 0.64, "#3e8cf0"),
          ("社科历史", "23%", 0.46, "#8b8375"),
      ], "#3e8cf0")])

# ══ 英语（拆 3）═══════════════════════════════════════════
C("06_英语记单词", "英语记单词", "词库 · 复习 · 留存", "VOCAB", "#f0623e", "english",
  ("词库记忆进度", "72", "%"), [
      blk_section(170, "每日新词 + 复习 · 个"),
      gauge(84, 244, 40, 0.72, "#f0623e", read="72", unit="PCT"),
      histogram(146, 196, 228, 66, [50, 68, 42, 80, 76, 92, 60], "#f0623e",
                ["MO", "TU", "WE", "TH", "FR", "SA", "SU"], 100),
      label(146, 288, "累计 1440 / 2000 词", MUTED, 9),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("已掌握", "74%", 0.74, "#f0623e"),
          ("复习中", "18%", 0.24, "#f0623e"),
          ("待学习", "8%", 0.10, "#8b8375"),
      ], "#f0623e")])

C("07_英语听力", "英语听力", "精听 · 泛听 · 听写", "LISTEN", "#ff8f4d", "english",
  ("听力训练完成度", "58", "%"), [
      blk_section(170, "每日精听 · 分钟"),
      gauge(84, 244, 40, 0.58, "#ff8f4d", read="58", unit="PCT"),
      histogram(146, 196, 228, 66, [20, 35, 25, 40, 30, 45, 28], "#ff8f4d",
                ["MO", "TU", "WE", "TH", "FR", "SA", "SU"], 50),
      label(146, 288, "累计 223min · 均值 32min", MUTED, 9),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("精听", "62%", 0.62, "#ff8f4d"),
          ("泛听", "55%", 0.55, "#ff8f4d"),
          ("听写", "48%", 0.48, "#8b8375"),
      ], "#ff8f4d")])

C("08_英语阅读", "英语阅读", "精读 · 长难句 · 语感", "READING", "#e0534c", "book",
  ("阅读理解准确率", "66", "%"), [
      blk_section(170, "每日阅读 · 篇"),
      gauge(84, 244, 40, 0.66, "#e0534c", read="66", unit="PCT"),
      sparkline(146, 200, 228, 60, [55, 60, 58, 68, 64, 72], "#e0534c",
                ["W1", "W2", "W3", "W4", "W5", "W6"]),
      label(146, 196, "近 6 周正确率 · %", MUTED, 10),
      numeral(146, 298, "72", "#e0534c", 20),
      label(174, 298, "% · 本周", MUTED, 10),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("精读", "70%", 0.70, "#e0534c"),
          ("长难句", "64%", 0.64, "#e0534c"),
          ("泛读", "58%", 0.58, "#8b8375"),
      ], "#e0534c")])

# ══ 数学（拆 2）═══════════════════════════════════════════
C("09_数学听课", "数学听课", "跟课 · 笔记 · 理解", "LECTURE", "#a78bfa", "sigma",
  ("课程跟课完成度", "88", "%"), [
      blk_section(170, "每日听课 · 分钟"),
      gauge(84, 244, 40, 0.88, "#a78bfa", read="88", unit="PCT"),
      histogram(146, 196, 228, 66, [45, 60, 50, 70, 65, 80, 55], "#a78bfa",
                ["MO", "TU", "WE", "TH", "FR", "SA", "SU"], 90),
      label(146, 288, "累计 425min · 已跟 14 讲", MUTED, 9),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("高数", "92%", 0.92, "#a78bfa"),
          ("线代", "85%", 0.85, "#a78bfa"),
          ("概率论", "74%", 0.74, "#8b8375"),
      ], "#a78bfa")])

C("10_数学做题", "数学做题", "刷题 · 错题 · 正确率", "PRACTICE", "#8b7bf5", "sigma",
  ("练习正确率", "72", "%"), [
      blk_section(170, "每日刷题 · 道"),
      gauge(84, 244, 40, 0.72, "#8b7bf5", read="72", unit="PCT"),
      sparkline(146, 200, 228, 60, [60, 68, 65, 76, 82, 88], "#8b7bf5",
                ["1", "2", "3", "4", "5", "6"]),
      label(146, 196, "近 6 次正确率 · %", MUTED, 10),
      numeral(146, 298, "38", "#8b7bf5", 20),
      label(174, 298, "道 · 本周", MUTED, 10),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("计算题", "78%", 0.78, "#8b7bf5"),
          ("证明题", "62%", 0.62, "#8b7bf5"),
          ("应用题", "70%", 0.70, "#8b8375"),
      ], "#8b7bf5")])

# ══ 专业课（拆 4）═════════════════════════════════════════
C("11_计算机网络", "计算机网络", "协议 · 分层 · 组网", "NETWORK", "#22b8cf", "chip",
  ("课程掌握度", "85", "%"), [
      blk_section(170, "章节完成 · %"),
      gauge(84, 244, 40, 0.85, "#22b8cf", read="85", unit="PCT"),
      histogram(146, 196, 228, 66, [90, 82, 88, 76, 92, 80, 86], "#22b8cf",
                ["1", "2", "3", "4", "5", "6", "7"], 100),
      label(146, 288, "已学 7 / 9 章 · 32h", MUTED, 9),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("TCP/IP", "88%", 0.88, "#22b8cf"),
          ("HTTP", "84%", 0.84, "#22b8cf"),
          ("子网划分", "72%", 0.72, "#8b8375"),
      ], "#22b8cf")])

C("12_计算机组成原理", "计算机组成原理", "CPU · 存储 · 指令", "ARCH", "#3ea8d8", "chip",
  ("课程掌握度", "72", "%"), [
      blk_section(170, "章节完成 · %"),
      gauge(84, 244, 40, 0.72, "#3ea8d8", read="72", unit="PCT"),
      histogram(146, 196, 228, 66, [80, 70, 75, 62, 78, 68, 74], "#3ea8d8",
                ["1", "2", "3", "4", "5", "6", "7"], 100),
      label(146, 288, "已学 6 / 8 章 · 24h", MUTED, 9),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("存储系统", "78%", 0.78, "#3ea8d8"),
          ("指令系统", "74%", 0.74, "#3ea8d8"),
          ("流水线", "60%", 0.60, "#8b8375"),
      ], "#3ea8d8")])

C("13_操作系统", "操作系统", "进程 · 内存 · 文件", "OS", "#2f9ecf", "chip",
  ("课程掌握度", "78", "%"), [
      blk_section(170, "章节完成 · %"),
      gauge(84, 244, 40, 0.78, "#2f9ecf", read="78", unit="PCT"),
      histogram(146, 196, 228, 66, [85, 78, 80, 70, 82, 76, 84], "#2f9ecf",
                ["1", "2", "3", "4", "5", "6", "7"], 100),
      label(146, 288, "已学 7 / 9 章 · 28h", MUTED, 9),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("进程管理", "84%", 0.84, "#2f9ecf"),
          ("内存管理", "78%", 0.78, "#2f9ecf"),
          ("文件系统", "68%", 0.68, "#8b8375"),
      ], "#2f9ecf")])

C("14_数据结构", "数据结构", "线性 · 树 · 图 · 排序", "DS", "#4a9eea", "chip",
  ("课程掌握度", "90", "%"), [
      blk_section(170, "章节完成 · %"),
      gauge(84, 244, 40, 0.90, "#4a9eea", read="90", unit="PCT"),
      histogram(146, 196, 228, 66, [95, 92, 88, 86, 94, 90, 92], "#4a9eea",
                ["1", "2", "3", "4", "5", "6", "7"], 100),
      label(146, 288, "已学 8 / 9 章 · 36h", MUTED, 9),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("树与图", "92%", 0.92, "#4a9eea"),
          ("排序查找", "90%", 0.90, "#4a9eea"),
          ("动态规划", "82%", 0.82, "#8b8375"),
      ], "#4a9eea")])

# ══ 政治 ══════════════════════════════════════════════════
C("15_政治", "政治", "理论 · 时政 · 背诵", "POLITICS", "#e0534c", "politics",
  ("背诵进度", "58", "%"), [
      blk_section(170, "每日背诵 · 分钟"),
      gauge(84, 244, 40, 0.58, "#e0534c", read="58", unit="PCT"),
      histogram(146, 196, 228, 66, [30, 45, 38, 52, 48, 60, 40], "#e0534c",
                ["MO", "TU", "WE", "TH", "FR", "SA", "SU"], 70),
      label(146, 288, "已背 12 / 21 章", MUTED, 9),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("时政热点", "74%", 0.74, "#e0534c"),
          ("题目练习", "66%", 0.66, "#e0534c"),
          ("理论背诵", "58%", 0.58, "#8b8375"),
      ], "#e0534c")])

# ══ 学习 ══════════════════════════════════════════════════
C("16_学习", "学习", "专注 · 积累 · 成长", "STUDY", "#3b82f6", "study",
  ("今日专注时长", "4.8", "h"), [
      blk_section(170, "周度趋势 · 小时"),
      gauge(84, 244, 40, 0.80, "#3b82f6", read="80", unit="PCT"),
      label(146, 196, "近 6 周专注时长", MUTED, 10),
      sparkline(146, 210, 228, 54, [18, 22, 20, 26, 24, 30], "#3b82f6",
                ["W1", "W2", "W3", "W4", "W5", "W6"]),
      numeral(146, 298, "6.0", "#3b82f6", 20),
      label(180, 298, "h · 本周目标", MUTED, 10),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("上午", "32%", 0.64, "#3b82f6"),
          ("下午", "40%", 0.80, "#3b82f6"),
          ("晚上", "28%", 0.56, "#8b8375"),
      ], "#3b82f6")])

# ══ 编程 ══════════════════════════════════════════════════
C("17_编程", "编程", "写码 · 调试 · 精进", "CODE", "#2f9e6e", "code",
  ("本周编码时长", "15", "h"), [
      blk_section(170, "每日刷题 · 道"),
      gauge(84, 244, 40, 0.75, "#2f9e6e", read="75", unit="PCT"),
      histogram(146, 196, 228, 66, [3, 5, 4, 6, 8, 5, 7], "#2f9e6e",
                ["MO", "TU", "WE", "TH", "FR", "SA", "SU"], 10),
      label(146, 288, "累计 38 道 · 均值 5.4", MUTED, 9),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("Java", "40%", 0.80, "#2f9e6e"),
          ("Python", "35%", 0.70, "#2f9e6e"),
          ("Kotlin", "25%", 0.50, "#8b8375"),
      ], "#2f9e6e")])

# ══ 生活类 ════════════════════════════════════════════════
C("18_逛街", "逛街", "购物 · 休闲 · 放松", "SHOP", "#e8497f", "shop",
  ("本月预算使用", "62", "%"), [
      blk_section(170, "单次花费 · 元"),
      gauge(84, 244, 40, 0.62, "#e8497f", read="62", unit="PCT"),
      histogram(146, 196, 228, 66, [120, 80, 200, 150, 90, 180], "#e8497f",
                ["1", "2", "3", "4", "5", "6"], 220),
      label(146, 288, "累计 ¥820 · 均值 ¥137", MUTED, 9),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("服饰美妆", "45%", 0.90, "#e8497f"),
          ("餐饮小吃", "32%", 0.64, "#e8497f"),
          ("文娱娱乐", "23%", 0.46, "#8b8375"),
      ], "#e8497f")])

C("19_看电影", "看电影", "观影 · 品味 · 记录", "FILM", "#b06ef8", "movie",
  ("本月观影", "7", "部"), [
      blk_section(170, "月度观影量 · 部"),
      gauge(84, 244, 40, 0.70, "#b06ef8", read="70", unit="PCT"),
      label(146, 196, "近 6 月观影数量", MUTED, 10),
      sparkline(146, 210, 228, 54, [4, 6, 5, 8, 7, 9], "#b06ef8",
                ["MAY", "JUN", "JUL", "AUG", "SEP", "OCT"]),
      numeral(146, 298, "9", "#b06ef8", 20),
      label(166, 298, "部 · 当前月", MUTED, 10),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("科幻动作", "38%", 0.76, "#b06ef8"),
          ("剧情文艺", "35%", 0.70, "#b06ef8"),
          ("喜剧动画", "27%", 0.54, "#8b8375"),
      ], "#b06ef8")])

C("20_吃饭", "吃饭", "美食 · 营养 · 记录", "FOOD", "#f0a02a", "food",
  ("今日三餐记录", "3/3", "餐"), [
      blk_section(170, "每日热量 · 百千卡"),
      gauge(84, 244, 40, 0.66, "#f0a02a", read="66", unit="PCT"),
      histogram(146, 196, 228, 66, [18, 21, 19, 23, 20, 25, 17], "#f0a02a",
                ["MO", "TU", "WE", "TH", "FR", "SA", "SU"], 28),
      label(146, 288, "均值 20.4 · 峰值 25", MUTED, 9),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("碳水主食", "45%", 0.90, "#f0a02a"),
          ("蛋白质", "32%", 0.64, "#f0a02a"),
          ("蔬果纤维", "23%", 0.46, "#8b8375"),
      ], "#f0a02a")])

C("21_爬山", "爬山", "登高 · 自然 · 挑战", "HIKE", "#4fae3f", "hike",
  ("本月登顶", "3", "座"), [
      blk_section(170, "单次爬升 · 百米"),
      gauge(84, 244, 40, 0.60, "#4fae3f", read="60", unit="PCT"),
      label(146, 196, "近 5 次爬升高度", MUTED, 10),
      sparkline(146, 210, 228, 54, [6, 9, 7, 12, 10], "#4fae3f",
                ["1", "2", "3", "4", "5"]),
      numeral(146, 298, "1200", "#4fae3f", 20),
      label(200, 298, "m · 最高单次", MUTED, 10),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("登山徒步", "48%", 0.96, "#4fae3f"),
          ("露营野餐", "30%", 0.60, "#4fae3f"),
          ("骑行郊游", "22%", 0.44, "#8b8375"),
      ], "#4fae3f")])

C("22_散步", "散步", "漫步 · 放松 · 思考", "WALK", "#2aa8a0", "walk",
  ("今日步数目标", "84", "%"), [
      blk_section(170, "每日步数 · 千步"),
      gauge(84, 244, 40, 0.84, "#2aa8a0", read="84", unit="PCT"),
      histogram(146, 196, 228, 66, [6, 8, 7, 9, 10, 8, 9], "#2aa8a0",
                ["MO", "TU", "WE", "TH", "FR", "SA", "SU"], 12),
      label(146, 288, "累计 8.4k / 10k 步", MUTED, 9),
      hairline(PAD, 310, W - PAD, HAIR, 1),
      meter_rows(PAD, 328, W - PAD * 2, [
          ("饭后傍晚", "52%", 0.90, "#2aa8a0"),
          ("午后阳光", "28%", 0.56, "#2aa8a0"),
          ("清晨空气", "20%", 0.40, "#8b8375"),
      ], "#2aa8a0")])




def main():
    here = os.path.dirname(os.path.abspath(__file__))
    out = os.path.join(here, "svg")
    os.makedirs(out, exist_ok=True)
    for c in CARDS:
        svg = card(c["theme"], c["subtitle"], c["code"], c["color"], c["icon"],
                   c["primary"], c["blocks"])
        with open(os.path.join(out, c["file"] + ".svg"), "w", encoding="utf-8") as f:
            f.write(svg)
        print("✓ %s.svg" % c["file"])
    print("\n共 %d 张 → %s" % (len(CARDS), out))


if __name__ == "__main__":
    main()
