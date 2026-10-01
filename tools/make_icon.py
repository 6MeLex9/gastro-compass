"""Генерация иконки приложения ГастроКомпас (лаунчер, PNG для всех плотностей)."""
import os
import math
from PIL import Image, ImageDraw

BASE = r"C:\Users\anna3\OneDrive\Документы\deepseek-harness\default-workspace\GastroCompass\app\src\main\res"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

BG_TOP = (0x2E, 0x7D, 0x63)      # глубокий зелёный
BG_BOTTOM = (0x1B, 0x5E, 0x54)   # тёмно-бирюзовый
ACCENT = (0x9F, 0xE0, 0xB0)      # мягкий лайм
WHITE = (0xFF, 0xFF, 0xFF)


def rounded_mask(size, radius_ratio=0.22):
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    r = int(size * radius_ratio)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=255)
    return mask


def circle_mask(size):
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.ellipse([0, 0, size - 1, size - 1], fill=255)
    return mask


def gradient(size):
    img = Image.new("RGB", (size, size), BG_TOP)
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / max(1, size - 1)
        col = tuple(int(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t) for i in range(3))
        d.line([(0, y), (size, y)], fill=col)
    return img


def draw_shield(d, size):
    """Щит — символ защиты слизистой."""
    cx = size / 2
    top = size * 0.20
    bottom = size * 0.83
    half_w = size * 0.27
    pts = [
        (cx - half_w, top + size * 0.02),
        (cx, top - size * 0.035),
        (cx + half_w, top + size * 0.02),
        (cx + half_w, size * 0.52),
        (cx, bottom),
        (cx - half_w, size * 0.52),
    ]
    d.polygon(pts, fill=WHITE)
    # внутренний "лист" — питание
    inner = [
        (cx, top + size * 0.11),
        (cx + half_w * 0.52, size * 0.45),
        (cx, size * 0.68),
        (cx - half_w * 0.52, size * 0.45),
    ]
    d.polygon(inner, fill=BG_TOP)
    d.line([(cx, top + size * 0.11), (cx, size * 0.68)], fill=ACCENT, width=max(1, int(size * 0.028)))
    # прожилки листа
    w = max(1, int(size * 0.022))
    d.line([(cx, size * 0.34), (cx + half_w * 0.34, size * 0.30)], fill=ACCENT, width=w)
    d.line([(cx, size * 0.46), (cx + half_w * 0.38, size * 0.44)], fill=ACCENT, width=w)
    d.line([(cx, size * 0.34), (cx - half_w * 0.34, size * 0.30)], fill=ACCENT, width=w)
    d.line([(cx, size * 0.46), (cx - half_w * 0.38, size * 0.44)], fill=ACCENT, width=w)


def build(size, mask):
    base = gradient(size)
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    draw_shield(d, size)
    base = Image.alpha_composite(base.convert("RGBA"), layer)
    base.putalpha(mask)
    return base


def main():
    for name, size in DENSITIES.items():
        folder = os.path.join(BASE, f"mipmap-{name}")
        os.makedirs(folder, exist_ok=True)
        build(size, rounded_mask(size)).save(os.path.join(folder, "ic_launcher.png"))
        build(size, circle_mask(size)).save(os.path.join(folder, "ic_launcher_round.png"))
        # foreground для adaptive icon (108dp холст, рисунок в центральных 66%)
        fg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        inner = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw_shield(ImageDraw.Draw(inner), size)
        scale = 0.62
        small = inner.resize((max(1, int(size * scale)),) * 2, Image.LANCZOS)
        off = (size - small.width) // 2
        fg.alpha_composite(small, (off, off))
        fg.save(os.path.join(folder, "ic_launcher_foreground.png"))
        print(f"{name}: {size}px ok")
    # adaptive icon
    ad = os.path.join(BASE, "mipmap-anydpi-v26")
    os.makedirs(ad, exist_ok=True)
    for fname in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(ad, fname), "w", encoding="utf-8") as f:
            f.write(
                '<?xml version="1.0" encoding="utf-8"?>\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@color/ic_launcher_background" />\n'
                '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
                '</adaptive-icon>\n'
            )
    print("adaptive icons ok")


if __name__ == "__main__":
    main()
