"""把「鱼灾」素材整理进 Android 资源目录。

只做三件事：改名、裁掉透明边、生成一张自适应图标前景。
不重绘任何图形——素材本身就是成品。
"""
from pathlib import Path
from PIL import Image

SRC = Path(r"E:\fangchenmi\build-artifacts\fishref")
RES = Path(r"E:\fangchenmi\app\src\main\res")
NODPI = RES / "drawable-nodpi"
NODPI.mkdir(parents=True, exist_ok=True)

# 素材文件名 -> Android 资源名
COPY = {
    "whale-wave.webp": "whale_wave",
    "whale-sleepy.webp": "whale_sleepy",
    "whale-excited.webp": "whale_excited",
    "whale-surprised.webp": "whale_surprised",
    "whale-happy.webp": "whale_happy",
    "fish-side.webp": "fish_side",
    "fish-front.webp": "fish_front",
    "fatfish-idle-0.webp": "fatfish_0",
    "fatfish-idle-1.webp": "fatfish_1",
    "fatfish-idle-2.webp": "fatfish_2",
    "fatfish-idle-3.webp": "fatfish_3",
}

for src_name, out_name in COPY.items():
    src = SRC / src_name
    if not src.exists():
        print("MISS", src_name)
        continue
    img = Image.open(src).convert("RGBA")
    bbox = img.split()[3].getbbox()
    if bbox:
        img = img.crop(bbox)
    dst = NODPI / f"{out_name}.webp"
    img.save(dst, "WEBP", quality=92, method=6)
    print(f"{out_name}.webp  {img.width}x{img.height}  {dst.stat().st_size // 1024}KB")

# 自适应图标前景：取上半身（头 + 肩），居中放进 432x432，留出安全边距
hero = Image.open(SRC / "whale-wave.webp").convert("RGBA")
bbox = hero.split()[3].getbbox()
hero = hero.crop(bbox)
head_h = int(hero.height * 0.52)
head = hero.crop((0, 0, hero.width, head_h))

canvas = 432
safe = int(canvas * 0.66)  # 自适应图标安全区
scale = min(safe / head.width, safe / head.height)
head = head.resize((int(head.width * scale), int(head.height * scale)), Image.LANCZOS)
icon = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
icon.paste(head, ((canvas - head.width) // 2, (canvas - head.height) // 2), head)
icon.save(NODPI / "ic_whale_foreground.png", "PNG", optimize=True)
print("ic_whale_foreground.png", (NODPI / "ic_whale_foreground.png").stat().st_size // 1024, "KB")
