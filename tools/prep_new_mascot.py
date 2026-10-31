"""把豆包生成的白底立绘处理成 Android 可用的透明 WebP。

做法：从四角泛洪填充白色背景（角色有深色描边，填充不会漏进衣服），
再把标记色替换为透明，最后裁边、缩放。
"""
from PIL import Image, ImageDraw
import numpy as np
import os

SRC = r"E:\fangchenmi\素材候选\新角色"
DST = r"E:\fangchenmi\app\src\main\res\drawable-nodpi"

MARKER = (255, 0, 255)


def cut_background(path: str) -> Image.Image:
    img = Image.open(path).convert("RGBA")
    rgb = img.convert("RGB")
    w, h = rgb.size

    # 四角泛洪；thresh 覆盖抗锯齿的浅灰边，但深色描边会挡住它
    for xy in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1), (w // 2, 0), (w // 2, h - 1)]:
        try:
            ImageDraw.floodfill(rgb, xy, MARKER, thresh=72)
        except Exception:
            pass

    arr = np.array(rgb)
    mask = (arr[:, :, 0] == MARKER[0]) & (arr[:, :, 1] == MARKER[1]) & (arr[:, :, 2] == MARKER[2])

    out = np.array(img)
    out[mask, 3] = 0

    # 边缘去白晕：把紧邻透明区、且本身很亮的像素做成半透明
    alpha = out[:, :, 3]
    rgbp = out[:, :, :3].astype(np.int16)
    near_white = (rgbp[:, :, 0] > 235) & (rgbp[:, :, 1] > 235) & (rgbp[:, :, 2] > 235)
    transparent = alpha == 0
    # 向外膨胀一圈判断相邻透明
    pad = np.pad(transparent, 1, mode="edge")
    adjacent = (
        pad[0:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, 0:-2] | pad[1:-1, 2:] |
        pad[0:-2, 0:-2] | pad[0:-2, 2:] | pad[2:, 0:-2] | pad[2:, 2:]
    )
    soften = near_white & adjacent & (~transparent)
    out[soften, 3] = 90

    result = Image.fromarray(out, "RGBA")
    bbox = result.split()[3].getbbox()
    return result.crop(bbox) if bbox else result


def save_webp(img: Image.Image, name: str, max_side: int) -> None:
    w, h = img.size
    scale = min(1.0, max_side / max(w, h))
    if scale < 1.0:
        img = img.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.LANCZOS)
    path = os.path.join(DST, name)
    img.save(path, "WEBP", quality=92, method=6)
    print(f"{name}: {img.size[0]}x{img.size[1]}  {os.path.getsize(path) // 1024} KB")


def make_icon(img: Image.Image) -> None:
    canvas = 432  # adaptive icon foreground 的安全尺寸
    target = int(canvas * 0.60)
    w, h = img.size
    scale = target / max(w, h)
    img = img.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.LANCZOS)
    layer = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    layer.paste(img, ((canvas - img.size[0]) // 2, (canvas - img.size[1]) // 2), img)
    path = os.path.join(DST, "ic_whale_foreground.png")
    layer.save(path, "PNG", optimize=True)
    print(f"ic_whale_foreground.png: {canvas}x{canvas}  {os.path.getsize(path) // 1024} KB")


mapping = [
    ("idle.png", "whale_wave.webp"),
    ("sleep.png", "whale_sleepy.webp"),
    ("surprised.png", "whale_surprised.webp"),
]

for src_name, dst_name in mapping:
    src = os.path.join(SRC, src_name)
    if not os.path.exists(src):
        print(f"缺文件: {src}")
        continue
    cut = cut_background(src)
    save_webp(cut, dst_name, 860)

icon_src = os.path.join(SRC, "icon.png")
if os.path.exists(icon_src):
    make_icon(cut_background(icon_src))
