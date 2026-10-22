from PIL import Image, ImageDraw, ImageFont

base_img = Image.open('tv_screen.png')
font_path = 'launcher/app/src/main/res/font/outfit.ttf'

s = {'subuh': '03:53', 'dzuhur': '11:21', 'ashar': '14:30', 'maghrib': '17:30', 'isya': '18:40'}

def make_mockup(sp_size, text, loc_text, filename):
    img = base_img.copy()
    draw = ImageDraw.Draw(img)
    
    # 1. Clear the old prayer line area (y: 145 to 190, x: 70 to 860) with black
    draw.rectangle([70, 145, 860, 190], fill=(0, 0, 0))
    # Clear under date as well (y: 115 to 145, x: 250 to 500)
    draw.rectangle([250, 115, 500, 145], fill=(0, 0, 0))
    
    # 2. Draw location under date (x=257, y=120)
    font_loc = ImageFont.truetype(font_path, int(round(12 * 1.33125)))
    draw.text((257, 120), loc_text, font=font_loc, fill=(180, 180, 180))
    
    # 3. Draw prayer schedule below (x=76, y=156)
    px = int(round(sp_size * 1.33125))
    font_sched = ImageFont.truetype(font_path, px)
    draw.text((76, 156), text, font=font_sched, fill=(225, 225, 225))
    
    # Draw a subtle vertical guide line at x=453 (end of OKTOBER) for visual inspection
    # draw.line([(453, 90), (453, 180)], fill=(255, 0, 0), width=1)
    
    # Crop top region: y: 20 to 220, x: 40 to 900
    crop = img.crop((40, 20, 950, 220))
    crop.save(filename)
    print(f'Saved {filename}')

txt_dot = f"Subuh {s['subuh']}  ·  Dzuhur {s['dzuhur']}  ·  Ashar {s['ashar']}  ·  Maghrib {s['maghrib']}  ·  Isya {s['isya']}"
txt_dot_norm = f"Subuh {s['subuh']} · Dzuhur {s['dzuhur']} · Ashar {s['ashar']} · Maghrib {s['maghrib']} · Isya {s['isya']}"
txt_tight = f"Subuh {s['subuh']}  Dzuhur {s['dzuhur']}  Ashar {s['ashar']}  Maghrib {s['maghrib']}  Isya {s['isya']}"

make_mockup(11.0, txt_dot_norm, "KAB. BLORA", "mockup_11sp_dot.png")
make_mockup(10.5, txt_dot_norm, "KAB. BLORA", "mockup_10.5sp_dot.png")
make_mockup(11.5, txt_dot_norm, "KAB. BLORA", "mockup_11.5sp_dot.png")
make_mockup(11.0, txt_tight, "KAB. BLORA", "mockup_11sp_spaces.png")
