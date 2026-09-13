# -*- coding: utf-8 -*-
"""生成「记一笔」应用图标（PWA / Android 主屏幕）。

输出：
  icons/icon-192.png           普通图标 192
  icons/icon-512.png           普通图标 512
  icons/icon-maskable-512.png  Android 自适应图标（全出血，内容内缩）

图形：蓝紫渐变圆角方块 + 白色人民币符号 ¥
"""
import os
from PIL import Image, ImageDraw

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'icons')

# 品牌渐变色（Material Design 3 主色）
C_TOP = (103, 80, 164)      # #6750A4  M3 primary
C_BOTTOM = (127, 103, 190)  # #7F67BE


def gradient(size):
    """对角线性渐变底图。"""
    img = Image.new('RGB', (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            t = (x * 0.35 + y * 0.65) / (size - 1)
            t = max(0.0, min(1.0, t))
            px[x, y] = (
                int(C_TOP[0] + (C_BOTTOM[0] - C_TOP[0]) * t),
                int(C_TOP[1] + (C_BOTTOM[1] - C_TOP[1]) * t),
                int(C_TOP[2] + (C_BOTTOM[2] - C_TOP[2]) * t),
            )
    return img


def draw_yuan(draw, size, scale, width_ratio=0.072):
    """在画布中心绘制白色 ¥ 符号。scale 为内容缩放比。"""
    cx = size / 2
    s = size * scale
    w = max(2, int(size * width_ratio))

    top = cx - s * 0.46
    join = cx + s * 0.02
    bottom = cx + s * 0.46
    half_arm = s * 0.30
    half_bar = s * 0.27
    bar1 = join + s * 0.10
    bar2 = join + s * 0.24

    def line(p1, p2):
        draw.line([p1, p2], fill=(255, 255, 255, 255), width=w, joint='curve')
        # 圆头：在端点补圆
        r = w / 2.0
        for (x, y) in (p1, p2):
            draw.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, 255))

    line((cx - half_arm, top), (cx, join))
    line((cx + half_arm, top), (cx, join))
    line((cx, join), (cx, bottom))
    line((cx - half_bar, bar1), (cx + half_bar, bar1))
    line((cx - half_bar, bar2), (cx + half_bar, bar2))


def rounded_mask(size, radius_ratio=0.225):
    mask = Image.new('L', (size * 4, size * 4), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle(
        [0, 0, size * 4 - 1, size * 4 - 1],
        radius=int(size * 4 * radius_ratio),
        fill=255,
    )
    return mask.resize((size, size), Image.LANCZOS)


def make_icon(size, path, maskable=False):
    scale = 0.60 if maskable else 0.68
    base = gradient(size).convert('RGBA')

    layer = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw_yuan(ImageDraw.Draw(layer), size, scale)
    base = Image.alpha_composite(base, layer)

    if not maskable:
        base.putalpha(rounded_mask(size))

    base.save(path, 'PNG', optimize=True)
    print('  已生成', os.path.relpath(path), '(%dx%d)' % (size, size))


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    print('生成应用图标：')
    make_icon(192, os.path.join(OUT_DIR, 'icon-192.png'))
    make_icon(512, os.path.join(OUT_DIR, 'icon-512.png'))
    make_icon(512, os.path.join(OUT_DIR, 'icon-maskable-512.png'), maskable=True)
    print('完成。')


if __name__ == '__main__':
    main()
