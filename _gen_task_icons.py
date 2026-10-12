# -*- coding: utf-8 -*-
"""FlowTime 任务图标生成器 v3 —— 22 个细项图标，多色填充手绘风。

输出双份：
  ① 浏览器预览  svg/ic_task_*.svg
  ② Android     app/src/main/res/drawable/ic_task_*.xml  (VectorDrawable)

设计规范（与旧 iconfont 体系对齐）：
- 视窗 1024×1024（沿用旧资源坐标空间，Room 里的旧 index 不受影响）
- 每图标 2–4 个 <path>，各自 fillColor，形成多色层次
- 主色 + 亮色 + 深色三层，圆角几何造型
"""
import os

# ── 路径片段 ──────────────────────────────────────────────
V = 1024  # 视窗


def pt(*pairs):
    """把 (x,y) 序列拼成 M/L/Z 路径（绝对坐标，1024 网格）"""
    out = []
    for i, (x, y) in enumerate(pairs):
        out.append(("M" if i == 0 else "L") + "%d %d" % (x, y))
    return "".join(out) + "Z"


def rect(x, y, w, h, r=0):
    if r <= 0:
        return "M%d %d H%d V%d H%d Z" % (x, y, x + w, y + h, x)
    return (
        "M%d %d H%d A%d %d 0 0 1 %d %d V%d A%d %d 0 0 1 %d %d H%d "
        "A%d %d 0 0 1 %d %d V%d A%d %d 0 0 1 %d %d Z"
        % (x + r, y, x + w - r, r, r, x + w, y + r, y + h - r, r, r, x + w - r, y + h,
           x + r, r, r, x, y + h - r, y + r, r, r, x + r, y)
    )


def circ(cx, cy, r):
    return ("M%d %d m-%d 0 a%d %d 0 1 0 %d 0 a%d %d 0 1 0 -%d 0 Z"
            % (cx, cy, r, r, r, 2 * r, r, r, 2 * r))


def ring(cx, cy, r, w):
    """圆环（外圆 + 内圆挖空，用 fill-rule evenodd 需两个子路径；这里用外圆减内圆近似为描边环）"""
    ro, ri = r, r - w
    return (
        "M%d %d m-%d 0 a%d %d 0 1 0 %d 0 a%d %d 0 1 0 -%d 0 Z "
        "M%d %d m-%d 0 a%d %d 0 1 0 %d 0 a%d %d 0 1 0 -%d 0 Z"
        % (cx, cy, ro, ro, ro, 2 * ro, ro, ro, 2 * ro,
           cx, cy, ri, ri, ri, 2 * ri, ri, ri, 2 * ri)
    )


# ── 22 个图标：(文件名, 中文, [(pathData, fillColor, evenOdd?)]) ──
ICONS = []


def add(name, cn, layers):
    ICONS.append((name, cn, layers))


# 英语记单词 —— 单词卡 + A / 文
add("english", "英语记单词", [
    # 底卡（后）
    (rect(272, 256, 640, 480, 56), "#F0623E"),
    (rect(336, 320, 512, 352, 32), "#FFFFFF"),
    # 顶层卡（前，错位）
    (rect(112, 384, 512, 400, 48), "#FF8F4D"),
    (rect(168, 440, 400, 288, 24), "#FFF4EC"),
    # 左卡字母 A
    ("M256 656 L336 520 L416 656 M290 616 H382", "#E0534C", 34),
    # 右卡中文/单词行
    (rect(560, 440, 216, 44, 22), "#F0623E"),
    (rect(560, 528, 168, 44, 22), "#F0623E"),
    (rect(560, 616, 200, 44, 22), "#F0623E"),
    # 顶部小 ABC 标
    (rect(456, 168, 240, 96, 28), "#FBD9C8"),
    (rect(492, 200, 40, 32, 8), "#E0534C"),
    (rect(552, 200, 40, 32, 8), "#E0534C"),
    (rect(612, 200, 40, 32, 8), "#E0534C"),
])

# 英语听力 —— 耳机
add("listening", "英语听力", [
    ("M256 512 A256 256 0 0 1 768 512 V704 H640 V512 A128 128 0 0 0 384 512 V704 H256 Z", "#FF8F4D"),
    (rect(192, 640, 192, 256, 72), "#E0534C"),
    (rect(640, 640, 192, 256, 72), "#E0534C"),
])

# 英语阅读 —— 打开的书 + EN 书签
add("reading", "英语阅读", [
    ("M128 288 Q512 192 512 320 L512 800 Q512 672 128 768 Z", "#3E8CF0"),
    ("M896 288 Q512 192 512 320 L512 800 Q512 672 896 768 Z", "#6FD3E8"),
    (rect(480, 320, 64, 480, 32), "#FFFFFF"),
    (rect(608, 432, 208, 80, 20), "#FFFFFF"),
    (rect(636, 456, 44, 32, 8), "#1E6FDB"),
    (rect(692, 456, 44, 32, 8), "#1E6FDB"),
    (rect(748, 456, 44, 32, 8), "#1E6FDB"),
    ("M224 448 H416 M224 560 H368", "#BFE0FF", 30),
])

# 数学听课 —— 黑板 + 公式
add("lecture", "数学听课", [
    (rect(96, 160, 832, 576, 48), "#2E6B4F"),
    (rect(160, 224, 704, 384, 24), "#1E4A36"),
    ("M256 384 H448 M256 480 H384 M544 384 L736 512 M736 384 L544 512", "#A7F3D0", 30),
    (rect(416, 736, 192, 40, 20), "#8B8375"),
])

# 数学做题 —— 试卷 + 铅笔
add("practice", "数学做题", [
    # 纸张：米白底 + 可见描边（用 rect 描边近似：深色大矩形 + 内层米白矩形）
    (rect(112, 96, 640, 832, 52), "#8B5CF6"),
    (rect(144, 128, 576, 768, 36), "#FDFBF6"),
    # 卷头标题
    ("M224 272 H560", "#7C3AED", 34),
    ("M224 384 H608 M224 480 H544 M224 576 H608 M224 672 H496", "#D6CCF7", 28),
    # 铅笔（右下，压在纸上）
    ("M608 560 L848 320 L928 400 L688 640 Z", "#7C3AED"),
    ("M848 320 L928 400 L976 352 L896 272 Z", "#FDE68A"),
    ("M608 560 L688 640 L544 704 Z", "#C4B5FD"),
    ("M544 704 L608 560 L688 640 Z", "#6D28D9"),
])

# 计算机网络 —— 地球 + 连线
add("network", "计算机网络", [
    (ring(512, 512, 352, 96), "#22B8CF"),
    ("M160 512 H864", "#0E7490", 30),
    ("M512 160 A224 352 0 0 1 512 864 A224 352 0 0 1 512 160 Z", "#67E8F9"),
    (circ(512, 512, 512 - 448), "#0E7490"),
])

# 计算机组成原理 —— CPU 芯片（引脚从芯片边缘向外伸出）
add("arch", "计算机组成原理", [
    # 引脚：从芯片本体外缘向外延伸
    ("M380 288 V112 M512 288 V112 M644 288 V112 "
     "M380 736 V912 M512 736 V912 M644 736 V912 "
     "M288 380 H112 M288 512 H112 M288 644 H112 "
     "M736 380 H912 M736 512 H912 M736 644 H912", "#38BDF8", 40),
    # 芯片本体（盖住引脚内端）
    (rect(240, 240, 544, 544, 44), "#0C4A6E"),
    (rect(304, 304, 416, 416, 32), "#3EA8D8"),
    # 中心核心 + 十字走线
    (rect(432, 432, 160, 160, 20), "#E0F2FE"),
    ("M352 512 H672 M512 352 V672", "#7DD3FC", 26),
])

# 操作系统 —— 齿轮
add("os", "操作系统", [
    # 八齿
    ("M448 128 H576 L600 240 L704 176 L800 272 L736 376 L848 400 V528 L736 552 "
     "L800 656 L704 752 L600 688 L576 800 H448 L424 688 L320 752 L224 656 "
     "L288 552 L176 528 V400 L288 376 L224 272 L320 176 L424 240 Z", "#2F9ECF"),
    # 中心孔
    (circ(512, 464, 152), "#E0F2FE"),
    (circ(512, 464, 72), "#1E6F96"),
])

# 数据结构 —— 二叉树
add("ds", "数据结构", [
    # 连线
    ("M512 288 V400 M512 400 L288 512 M512 400 L736 512 "
     "M288 640 V704 M736 640 V704", "#60A5FA", 34),
    # 根 / 二层
    (circ(512, 224, 88), "#4A9EEA"),
    (circ(288, 576, 76), "#93C5FD"),
    (circ(736, 576, 76), "#93C5FD"),
    # 叶子
    (circ(288, 800, 64), "#FDE68A"),
    (circ(736, 800, 64), "#FDE68A"),
])

# 目标 —— 靶心
add("goal", "目标", [
    (ring(512, 512, 384, 96), "#F2545B"),
    (ring(512, 512, 224, 96), "#F9A03F"),
    (circ(512, 512, 128), "#FFD166"),
    ("M512 512 L832 192", "#E0534C", 34),
    ("M832 192 L896 128 L896 224 Z", "#E0534C"),
])

# 复盘 —— 循环箭头
add("review", "复盘", [
    ("M768 512 A256 256 0 1 1 512 256", "#8F82F5", 56),
    ("M512 160 L656 256 L512 352 Z", "#8F82F5"),
    (circ(512, 512, 96), "#DDD6FE"),
])

# 反省 —— 镜子 / 自省
add("reflect", "反省", [
    (ring(512, 448, 320, 96), "#9D74F8"),
    (rect(448, 736, 128, 176, 32), "#6D28D9"),
    (circ(512, 448, 160), "#E9D5FF"),
    (circ(512, 416, 48), "#9D74F8"),
    ("M416 576 Q512 656 608 576", "#9D74F8", 30),
])

# 运动 —— 哑铃
add("sport", "运动", [
    (rect(128, 416, 112, 192, 32), "#33C48D"),
    (rect(240, 352, 128, 320, 40), "#2FB984"),
    (rect(656, 352, 128, 320, 40), "#2FB984"),
    (rect(784, 416, 112, 192, 32), "#33C48D"),
    (rect(368, 480, 288, 64, 32), "#A7F3D0"),
])

# 学习 / 笔记 —— 笔记本 + 铅笔
add("read", "学习", [
    # 本子
    (rect(160, 144, 576, 736, 48), "#F9A03F"),
    (rect(224, 208, 448, 608, 24), "#FFF7EA"),
    # 装订环
    (rect(120, 256, 80, 56, 28), "#C67C1E"),
    (rect(120, 464, 80, 56, 28), "#C67C1E"),
    (rect(120, 672, 80, 56, 28), "#C67C1E"),
    # 横线
    ("M304 352 H608 M304 480 H576 M304 608 H608", "#E7CBA4", 26),
    # 铅笔
    ("M688 704 L832 560 L896 624 L752 768 Z", "#F2545B"),
    ("M832 560 L896 624 L928 592 L864 528 Z", "#FFD8B0"),
    ("M688 704 L752 768 L640 800 Z", "#C9A227"),
])

# 时间
add("time", "时间", [
    (ring(512, 512, 384, 96), "#3B82F6"),
    ("M512 288 V512 L672 608", "#1E40AF", 34),
])

# 代码 —— </>
add("code", "编程", [
    (rect(96, 224, 832, 576, 64), "#0F172A"),
    (rect(96, 224, 832, 96, 64), "#334155"),
    (circ(192, 272, 24), "#F87171"),
    (circ(272, 272, 24), "#FBBF24"),
    (circ(352, 272, 24), "#34D399"),
    ("M416 512 L272 656 L416 800", "#2F9E6E", 36),
    ("M608 512 L752 656 L608 800", "#2F9E6E", 36),
    ("M560 448 L464 864", "#A7F3D0", 30),
])

# 计算机工程
add("computer", "计算机工程", [
    (rect(96, 224, 832, 512, 48), "#64748B"),
    (rect(160, 288, 704, 384, 24), "#0EA5E9"),
    (rect(384, 736, 256, 48, 24), "#475569"),
    (rect(304, 784, 416, 48, 24), "#334155"),
])

# 游戏
add("game", "游戏", [
    ("M288 448 H736 A160 160 0 0 1 896 608 L864 736 A64 64 0 0 1 752 768 L672 672 "
     "H352 L272 768 A64 64 0 0 1 160 736 L128 608 A160 160 0 0 1 288 448 Z", "#2FB984"),
    ("M320 544 V640 M272 592 H368", "#FFFFFF", 34),
    (circ(704, 560, 32), "#7ED957"),
    (circ(768, 624, 32), "#7ED957"),
])

# 政治
add("politics", "政治", [
    ("M96 384 L512 160 L928 384 Z", "#E0534C"),
    (rect(224, 416, 64, 320, 24), "#F0803C"),
    (rect(480, 416, 64, 320, 24), "#F0803C"),
    (rect(736, 416, 64, 320, 24), "#F0803C"),
    (rect(128, 768, 768, 96, 32), "#D6404A"),
])

# 柱状图
add("barchart", "柱状图", [
    (rect(160, 544, 160, 320, 24), "#3E8CF0"),
    (rect(416, 320, 160, 544, 24), "#6FD3E8"),
    (rect(672, 448, 160, 416, 24), "#2E86DE"),
    (rect(96, 864, 832, 48, 24), "#1E40AF"),
])

# 奖
add("award", "奖", [
    (circ(512, 416, 256), "#F5B301"),
    (circ(512, 416, 160), "#FFD166"),
    ("M416 640 L352 896 L512 800 L672 896 L608 640 Z", "#F0803C"),
])

# empty —— 空态
add("empty", "empty", [
    ("M224 384 L512 224 L800 384 V768 L512 928 L224 768 Z", "#CBD5E1"),
    ("M224 384 L512 544 L800 384", "#94A3B8", 26),
    ("M512 544 V928", "#94A3B8", 26),
])

# 跑步
add("run", "跑步", [
    (circ(704, 256, 88), "#7ED957"),
    ("M608 416 L448 544 L608 672 L688 896 L608 896 L512 704 L352 576 L480 448 Z", "#2FB984"),
    ("M448 544 L256 512 L192 640", "#2FB984", 36),
    ("M640 448 L832 512 L800 672", "#2FB984", 36),
])


# ── 输出 ──────────────────────────────────────────────────
# layer 两种形式：
#   (pathData, color)          → 填充（闭合图形）
#   (pathData, color, stroke)  → 描边（开放折线；stroke 为线宽，1024 网格）
# 注意：直线段 path 若只给 fill 会「零面积」不可见，必须走 stroke 形式。
def _split(layers):
    fills, strokes = [], []
    for item in layers:
        if len(item) >= 3:
            strokes.append(item)
        else:
            fills.append(item)
    return fills, strokes


def svg_doc(layers):
    fills, strokes = _split(layers)
    parts = ['<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" '
             'viewBox="0 0 %d %d">' % (V, V)]
    for d, col in fills:
        parts.append('<path d="%s" fill="%s"/>' % (d, col))
    for d, col, sw in strokes:
        parts.append('<path d="%s" fill="none" stroke="%s" stroke-width="%d" '
                     'stroke-linecap="round" stroke-linejoin="round"/>' % (d, col, sw))
    parts.append("</svg>")
    return "\n".join(parts)


def vector_doc(layers):
    fills, strokes = _split(layers)
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="24dp"',
        '    android:height="24dp"',
        '    android:viewportWidth="%d"' % V,
        '    android:viewportHeight="%d">' % V,
    ]
    for d, col in fills:
        lines.append('    <path android:fillColor="%s" android:pathData="%s"/>' % (col, d))
    for d, col, sw in strokes:
        lines.append(
            '    <path android:strokeColor="%s" android:strokeWidth="%d" '
            'android:strokeLineCap="round" android:strokeLineJoin="round" '
            'android:fillColor="#00000000" android:pathData="%s"/>' % (col, sw, d))
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    prev = os.path.join(here, "svg")
    res = os.path.join(here, "app", "src", "main", "res", "drawable")
    os.makedirs(prev, exist_ok=True)
    os.makedirs(res, exist_ok=True)

    for name, cn, layers in ICONS:
        with open(os.path.join(prev, "ic_task_%s.svg" % name), "w", encoding="utf-8") as f:
            f.write(svg_doc(layers))
        with open(os.path.join(res, "ic_task_%s.xml" % name), "w", encoding="utf-8") as f:
            f.write(vector_doc(layers))
        print("✓ ic_task_%-12s %s" % (name, cn))
    print("\n共 %d 个 → svg/ + res/drawable/" % len(ICONS))


if __name__ == "__main__":
    main()
