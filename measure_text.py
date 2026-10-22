from PIL import Image, ImageDraw, ImageFont

font_path = 'launcher/app/src/main/res/font/outfit.ttf'

s = {'subuh': '03:53', 'dzuhur': '11:21', 'ashar': '14:30', 'maghrib': '17:30', 'isya': '18:40'}

variations = [
    ('v1_dot_12sp', f"Subuh {s['subuh']} · Dzuhur {s['dzuhur']} · Ashar {s['ashar']} · Maghrib {s['maghrib']} · Isya {s['isya']}", 12),
    ('v2_dot_11sp', f"Subuh {s['subuh']} · Dzuhur {s['dzuhur']} · Ashar {s['ashar']} · Maghrib {s['maghrib']} · Isya {s['isya']}", 11),
    ('v3_dot_10.5sp', f"Subuh {s['subuh']} · Dzuhur {s['dzuhur']} · Ashar {s['ashar']} · Maghrib {s['maghrib']} · Isya {s['isya']}", 10.5),
    ('v4_dot_10sp', f"Subuh {s['subuh']} · Dzuhur {s['dzuhur']} · Ashar {s['ashar']} · Maghrib {s['maghrib']} · Isya {s['isya']}", 10),
    ('v5_tight_dot', f"Subuh {s['subuh']} • Dzuhur {s['dzuhur']} • Ashar {s['ashar']} • Maghrib {s['maghrib']} • Isya {s['isya']}", 11),
    ('v6_space_only_11sp', f"Subuh {s['subuh']}   Dzuhur {s['dzuhur']}   Ashar {s['ashar']}   Maghrib {s['maghrib']}   Isya {s['isya']}", 11),
    ('v7_space_only_10.5sp', f"Subuh {s['subuh']}   Dzuhur {s['dzuhur']}   Ashar {s['ashar']}   Maghrib {s['maghrib']}   Isya {s['isya']}", 10.5),
]

target_start = 76
target_end = 453
target_width = target_end - target_start

print(f'TARGET: start={target_start}, end={target_end}, width={target_width}')

for name, txt, sp in variations:
    px = int(round(sp * 1.33125))
    font = ImageFont.truetype(font_path, px)
    bbox = font.getbbox(txt)
    w = bbox[2] - bbox[0]
    end_x = target_start + w
    diff = end_x - target_end
    print(f'{name:20s}: sp={sp:4.1f} (px={px:2d}), width={w:3d}px, end_x={end_x:3d} (diff vs OKTOBER: {diff:+3d}px)')
