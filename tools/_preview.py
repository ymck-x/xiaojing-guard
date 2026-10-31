import sys
from PIL import Image
sys.path.insert(0, r'E:\fangchenmi\tools')
src = r'E:\fangchenmi\app\src\main\res\drawable-nodpi'
imgs = [('whale_wave.webp','normal'),('whale_sleepy.webp','sleep'),('whale_surprised.webp','surprised')]
H = 520
canvas = Image.new('RGBA',(1600,H),(11,27,51,255))
x = 60
for name,label in imgs:
    im = Image.open(rf'{src}\\{name}').convert('RGBA')
    s = H*0.86/max(im.size)
    im = im.resize((int(im.size[0]*s),int(im.size[1]*s)), Image.LANCZOS)
    canvas.alpha_composite(im,(x, (H-im.size[1])//2))
    x += im.size[0] + 60
canvas.convert('RGB').save(r'E:\fangchenmi\build-artifacts\mascot-preview.png')
print('ok')