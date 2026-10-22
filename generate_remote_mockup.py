from PIL import Image, ImageDraw, ImageFont

W, H = 420, 880
img = Image.new('RGB', (W, H), color=(15, 17, 21))
draw = ImageDraw.Draw(img)

font_path = 'launcher/app/src/main/res/font/outfit.ttf'
try:
    font_title = ImageFont.truetype(font_path, 19)
    font_sub = ImageFont.truetype(font_path, 13)
    font_btn = ImageFont.truetype(font_path, 14)
    font_sm = ImageFont.truetype(font_path, 11)
    font_ok = ImageFont.truetype(font_path, 18)
except:
    font_title = font_sub = font_btn = font_sm = font_ok = ImageFont.load_default()

# 1. Phone Top Bar / Header
draw.rectangle([0, 0, W, 64], fill=(22, 24, 29))
draw.line([(0, 64), (W, 64)], fill=(38, 42, 50), width=1)

# Device title & IP
draw.text((20, 14), "Xiaomi Android TV", font=font_title, fill=(255, 255, 255))
draw.ellipse([20, 42, 28, 50], fill=(52, 199, 89)) # Green status dot
draw.text((34, 38), "192.168.0.33 · Terhubung", font=font_sub, fill=(150, 155, 165))

# Power button (top right)
draw.rounded_rectangle([W - 54, 16, W - 18, 52], radius=10, fill=(45, 20, 24), outline=(140, 40, 50), width=1)
# Draw power icon: circle with vertical line
pcx, pcy = W - 36, 34
draw.arc([pcx - 8, pcy - 8, pcx + 8, pcy + 8], start=210, end=330, fill=(255, 75, 75), width=2)
draw.line([(pcx, pcy - 10), (pcx, pcy - 2)], fill=(255, 75, 75), width=2)

# 2. Live TV Screen Preview Container
live_y = 78
live_h = 180
live_w = 320
live_x = (W - live_w) // 2

# Outer frame of the TV preview
draw.rounded_rectangle([live_x - 4, live_y - 4, live_x + live_w + 4, live_y + live_h + 4], radius=14, fill=(30, 33, 40), outline=(50, 55, 65), width=1)

# Paste scaled TV screen
try:
    tv_screen = Image.open('hijri_screen2.png')
    tv_scaled = tv_screen.resize((live_w, live_h), Image.Resampling.LANCZOS)
    img.paste(tv_scaled, (live_x, live_y))
except Exception:
    draw.rectangle([live_x, live_y, live_x + live_w, live_y + live_h], fill=(0, 0, 0))

# Live badge overlay on top left of preview
draw.rounded_rectangle([live_x + 8, live_y + 8, live_x + 82, live_y + 28], radius=6, fill=(20, 20, 20), outline=(220, 50, 50), width=1)
draw.ellipse([live_x + 14, live_y + 14, live_x + 22, live_y + 22], fill=(255, 50, 50))
draw.text((live_x + 28, live_y + 11), "LIVE TV", font=font_sm, fill=(255, 255, 255))

# Toggle switch on top right of preview
draw.rounded_rectangle([live_x + live_w - 90, live_y + 8, live_x + live_w - 8, live_y + 28], radius=10, fill=(20, 35, 25), outline=(52, 199, 89), width=1)
draw.text((live_x + live_w - 80, live_y + 11), "Layar: ON", font=font_sm, fill=(110, 225, 130))

# 3. Text Input Box (Ketik Keyboard HP -> TV)
kb_y = live_y + live_h + 14
draw.rounded_rectangle([25, kb_y, W - 25, kb_y + 42], radius=10, fill=(25, 28, 34), outline=(45, 50, 60), width=1)
draw.text((36, kb_y + 12), "Ketik judul YouTube / film...", font=font_sub, fill=(110, 115, 125))

# Send button
draw.rounded_rectangle([W - 90, kb_y + 5, W - 30, kb_y + 37], radius=8, fill=(40, 100, 210))
draw.text((W - 78, kb_y + 12), "KIRIM", font=font_sm, fill=(255, 255, 255))

# 4. Nav Section: Back, D-Pad, Home
nav_y = kb_y + 56

# Back Button (Left of D-Pad)
draw.rounded_rectangle([25, nav_y + 60, 85, nav_y + 120], radius=14, fill=(28, 31, 38), outline=(50, 55, 66), width=1)
# Draw back arrow
bx, by = 55, nav_y + 82
draw.polygon([(bx - 10, by), (bx, by - 8), (bx, by + 8)], fill=(220, 220, 225))
draw.line([(bx, by), (bx + 8, by), (bx + 8, by + 8)], fill=(220, 220, 225), width=2)
draw.text((37, nav_y + 100), "KEMBALI", font=font_sm, fill=(140, 145, 155))

# Home Button (Right of D-Pad)
draw.rounded_rectangle([W - 85, nav_y + 60, W - 25, nav_y + 120], radius=14, fill=(28, 31, 38), outline=(50, 55, 66), width=1)
# Draw home house icon
hx, hy = W - 55, nav_y + 82
draw.polygon([(hx, hy - 9), (hx - 9, hy), (hx + 9, hy)], fill=(220, 220, 225))
draw.rectangle([hx - 7, hy, hx + 7, hy + 9], fill=(220, 220, 225))
draw.rectangle([hx - 3, hy + 3, hx + 3, hy + 9], fill=(28, 31, 38))
draw.text((W - 70, nav_y + 100), "BERANDA", font=font_sm, fill=(140, 145, 155))

# D-Pad Circle (Center)
dpad_cx, dpad_cy = W // 2, nav_y + 90
dpad_r = 90
draw.ellipse([dpad_cx - dpad_r, dpad_cy - dpad_r, dpad_cx + dpad_r, dpad_cy + dpad_r], fill=(26, 29, 36), outline=(55, 60, 72), width=2)

# Draw D-Pad Triangles
# Up
draw.polygon([(dpad_cx, dpad_cy - dpad_r + 14), (dpad_cx - 10, dpad_cy - dpad_r + 28), (dpad_cx + 10, dpad_cy - dpad_r + 28)], fill=(200, 205, 215))
# Down
draw.polygon([(dpad_cx, dpad_cy + dpad_r - 14), (dpad_cx - 10, dpad_cy + dpad_r - 28), (dpad_cx + 10, dpad_cy + dpad_r - 28)], fill=(200, 205, 215))
# Left
draw.polygon([(dpad_cx - dpad_r + 14, dpad_cy), (dpad_cx - dpad_r + 28, dpad_cy - 10), (dpad_cx - dpad_r + 28, dpad_cy + 10)], fill=(200, 205, 215))
# Right
draw.polygon([(dpad_cx + dpad_r - 14, dpad_cy), (dpad_cx + dpad_r - 28, dpad_cy - 10), (dpad_cx + dpad_r - 28, dpad_cy + 10)], fill=(200, 205, 215))

# Center OK button
ok_r = 36
draw.ellipse([dpad_cx - ok_r, dpad_cy - ok_r, dpad_cx + ok_r, dpad_cy + ok_r], fill=(38, 42, 52), outline=(70, 76, 90), width=1)
draw.text((dpad_cx - 13, dpad_cy - 11), "OK", font=font_ok, fill=(255, 255, 255))

# 5. Volume & Mute Controls
vol_y = nav_y + dpad_r * 2 + 20
vol_w = (W - 50 - 16) // 3

# Vol -
draw.rounded_rectangle([25, vol_y, 25 + vol_w, vol_y + 44], radius=10, fill=(28, 31, 38), outline=(50, 55, 66), width=1)
draw.text((25 + 26, vol_y + 13), "VOL -", font=font_btn, fill=(210, 215, 225))

# Mute
draw.rounded_rectangle([25 + vol_w + 8, vol_y, 25 + vol_w * 2 + 8, vol_y + 44], radius=10, fill=(28, 31, 38), outline=(50, 55, 66), width=1)
draw.text((25 + vol_w + 32, vol_y + 13), "MUTE", font=font_btn, fill=(210, 215, 225))

# Vol +
draw.rounded_rectangle([25 + vol_w * 2 + 16, vol_y, W - 25, vol_y + 44], radius=10, fill=(28, 31, 38), outline=(50, 55, 66), width=1)
draw.text((25 + vol_w * 2 + 40, vol_y + 13), "VOL +", font=font_btn, fill=(210, 215, 225))

# 6. Quick App Shortcuts (Buka Cepat)
app_y = vol_y + 60
draw.text((25, app_y), "PINTASAN APLIKASI CEPAT", font=font_sm, fill=(120, 125, 135))

app_btn_y = app_y + 20
app_w = (W - 50 - 24) // 4
apps = [
    ("YouTube", (180, 30, 30), (255, 255, 255)),
    ("Netflix", (20, 20, 20), (229, 9, 20)),
    ("Music", (160, 20, 40), (255, 255, 255)),
    ("Media", (50, 70, 190), (255, 255, 255))
]

for idx, (app_name, bg_c, fg_c) in enumerate(apps):
    x_btn = 25 + idx * (app_w + 8)
    draw.rounded_rectangle([x_btn, app_btn_y, x_btn + app_w, app_btn_y + 42], radius=10, fill=bg_c, outline=(60, 65, 75), width=1)
    tx = x_btn + (app_w - len(app_name) * 8) // 2
    draw.text((tx, app_btn_y + 13), app_name, font=font_sm, fill=fg_c)

# 7. Sleep Timer Bar
sleep_y = app_btn_y + 56
draw.rounded_rectangle([25, sleep_y, W - 25, sleep_y + 36], radius=9, fill=(22, 25, 30), outline=(40, 44, 54), width=1)
draw.text((38, sleep_y + 10), "Sleep Timer TV:   [ 30m ]   [ 60m ]   [ 90m ]", font=font_sm, fill=(140, 145, 155))

# Bottom note
draw.text((W // 2 - 110, H - 28), "Buka di Browser HP: 192.168.0.33:8080", font=font_sm, fill=(80, 85, 95))

img.save('remote_mockup.png')
print('Crisp mockup saved to remote_mockup.png')
