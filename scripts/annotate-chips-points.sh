#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p export
uv run --with pillow python - <<'PY2'
from PIL import Image, ImageDraw
from pathlib import Path
import json
sprite_path = Path('app/src/main/resources/test_sprite/chips.png')
project_path = Path('app/src/main/resources/tutorial/chips_breath_project.json')
out_path = Path('export/chips_points_overlay.png')
im = Image.open(sprite_path).convert('RGBA')
project = json.loads(project_path.read_text(encoding='utf-8'))
scale = 2
canvas = Image.new('RGBA', (im.width * scale, im.height * scale), (35, 37, 40, 255))
canvas.alpha_composite(im.resize((im.width * scale, im.height * scale), Image.Resampling.NEAREST), (0, 0))
d = ImageDraw.Draw(canvas)
for index, point in enumerate(project['points']):
    x = point['x'] * scale
    y = point['y'] * scale
    radius = point['radius'] * scale
    color = (255, 198, 80, 230) if point.get('shoulder') else ((70, 190, 255, 230) if point.get('animated') else (235, 235, 235, 230))
    d.ellipse((x - radius, y - radius, x + radius, y + radius), outline=color, width=2)
    d.ellipse((x - 7, y - 7, x + 7, y + 7), fill=color, outline=(0, 0, 0, 255), width=2)
    dx = point.get('offsetX', 0.0) * scale * 8
    dy = (point.get('offsetY', 0.0) + (-3.0 if point.get('shoulder') else 0.0)) * scale * 8
    d.line((x, y, x + dx, y + dy), fill=color, width=3)
    d.text((x + 10, y - 10), str(index), fill=(255, 255, 255, 255))
canvas.save(out_path)
print(out_path)
PY2
