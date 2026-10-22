from PIL import Image, ImageDraw, ImageFont

img = Image.open('final_home.png')
font_path = 'launcher/app/src/main/res/font/outfit.ttf'

# Let's crop the date/location region
# In final_home.png, date is at y: 100 to 140, x: 200 to 500
px_12 = int(round(12 * 1.33125))
font_12 = ImageFont.truetype(font_path, px_12)

# Make two mockups
for mode in ['title', 'caps']:
    test_img = img.copy()
    draw = ImageDraw.Draw(test_img)
    # Clear the location line: y: 120 to 142, x: 200 to 450
    draw.rectangle([200, 120, 450, 142], fill=(0, 0, 0))
    
    txt = "🕌  Kab. Blora" if mode == 'title' else "🕌  KAB. BLORA"
    # Draw text
    draw.text((205, 122), txt, font=font_12, fill=(200, 200, 200))
    
    crop = test_img.crop((50, 40, 600, 180))
    crop.save(f'test_mosque_{mode}.png')
    print(f'Saved test_mosque_{mode}.png')
