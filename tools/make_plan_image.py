from PIL import Image, ImageDraw, ImageFont

W = H = 1024
img = Image.new("RGB", (W, H), (10, 30, 60))
d = ImageDraw.Draw(img)
top, bottom = (10, 29, 58), (23, 58, 120)
for y in range(H):
    t = y / (H - 1)
    d.line([(0, y), (W, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))

# 气泡只放在上半部的空白处，绝不压字
for (cx, cy, r, w) in [(120, 170, 44, 2), (905, 120, 26, 2), (80, 420, 20, 2), (960, 470, 34, 2)]:
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(255, 255, 255), width=w)

mascot = Image.open(r"E:\fangchenmi\app\src\main\res\drawable-nodpi\whale_wave.webp").convert("RGBA")
scale = 430 / mascot.size[1]
mascot = mascot.resize((int(mascot.size[0] * scale), 430), Image.LANCZOS)
img.paste(mascot, ((W - mascot.size[0]) // 2, 120), mascot)

font_big = ImageFont.truetype(r"C:\Windows\Fonts\msyhbd.ttc", 104)
font_mid = ImageFont.truetype(r"C:\Windows\Fonts\msyh.ttc", 44)
font_small = ImageFont.truetype(r"C:\Windows\Fonts\msyh.ttc", 36)

def center(text, font, y, fill=(255, 255, 255)):
    box = d.textbbox((0, 0), text, font=font)
    d.text(((W - (box[2] - box[0])) / 2 - box[0], y), text, font=font, fill=fill)

center("小鲸守护", font_big, 640)
center("付费版 · 一次买断 19.9", font_mid, 800, (168, 208, 255))
center("不联网 · 不上传任何数据", font_small, 880, (138, 172, 216))

img.save(r"E:\fangchenmi\dist\afdian-plan.png", "PNG")
print("ok")