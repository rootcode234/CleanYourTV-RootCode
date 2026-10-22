from PIL import Image, ImageDraw, ImageFont

img = Image.open('pipe_screen.png')
font_path = 'launcher/app/src/main/res/font/outfit.ttf'

px_12 = int(round(12 * 1.33125))
px_11_5 = int(round(11.5 * 1.33125))
font_12 = ImageFont.truetype(font_path, px_12)
font_11_5 = ImageFont.truetype(font_path, px_11_5)

# Current time is ~19:50, so Subuh (besok) or next prayer
# Let's say next prayer is Subuh 03:53, or let's test highlighting Isya / Subuh

def create_preview(loc_text, next_prayer, filename):
    test_img = img.copy()
    draw = ImageDraw.Draw(test_img)
    
    # 1. Clear location area (y: 118 to 142, x: 200 to 520)
    draw.rectangle([200, 118, 520, 142], fill=(0, 0, 0))
    # Draw location text
    draw.text((205, 122), loc_text, font=font_12, fill=(200, 200, 200))
    
    # 2. Clear prayer schedule area (y: 145 to 175, x: 70 to 600)
    draw.rectangle([70, 145, 600, 175], fill=(0, 0, 0))
    
    # Draw prayer schedule with highlight
    # Let's say parts: Subuh 03:53 | Dzuhur 11:21 | Ashar 14:30 | Maghrib 17:30 | Isya 18:40
    prayers = [
        ("Subuh 03:53", "Subuh"),
        (" | ", None),
        ("Dzuhur 11:21", "Dzuhur"),
        (" | ", None),
        ("Ashar 14:30", "Ashar"),
        (" | ", None),
        ("Maghrib 17:30", "Maghrib"),
        (" | ", None),
        ("Isya 18:40", "Isya")
    ]
    
    cur_x = 75
    y = 153
    for text, p_name in prayers:
        if p_name == next_prayer:
            # Highlight: warm gold / amber (#FFE082) or bright cyan (#80D8FF) or bright white
            color = (255, 224, 130) # Warm gold #FFE082
        elif p_name is None:
            color = (120, 120, 120) # separator
        else:
            color = (180, 180, 180) # normal
        draw.text((cur_x, y), text, font=font_11_5, fill=color)
        bbox = font_11_5.getbbox(text)
        cur_x += (bbox[2] - bbox[0])
    
    crop = test_img.crop((50, 30, 750, 190))
    crop.save(filename)
    print(f'Saved {filename}')

create_preview("🕌  Kab. Blora  |  10 Jumadil Awal 1448 H", "Subuh", "preview_subuh_gold.png")
create_preview("🕌  Kab. Blora  ·  10 Jumadil Awal 1448 H", "Subuh", "preview_subuh_dot.png")
create_preview("🕌  Kab. Blora  |  10 Jumadil Awal 1448 H", "Isya", "preview_isya_gold.png")
