"""Generate legacy launcher PNGs for Zrmt (draws the same glyph as the adaptive icon)."""
from PIL import Image, ImageDraw

BG = (26, 28, 34, 255)          # #1A1C22
BLUE = (59, 130, 246, 255)      # #3B82F6
WHITE = (242, 244, 248, 255)    # #F2F4F8

def draw_icon(size: int, path: str):
    s = size / 108.0  # scale from the 108x108 adaptive-icon viewport
    img = Image.new("RGBA", (size, size), BG)
    d = ImageDraw.Draw(img)

    def box(x0, y0, x1, y1, extra=0.0):
        return [v * s for v in (x0, y0, x1, y1)], max(1, round(extra * s))

    # monitor outline
    pts, w = box(30, 36, 78, 64)
    d.rounded_rectangle(pts, radius=4 * s, outline=BLUE, width=w)
    # terminal prompt >
    pts, w = box(42, 45, 49, 55)
    d.line([(42 * s, 45 * s), (49 * s, 50 * s), (42 * s, 55 * s)],
           fill=WHITE, width=w, joint="curve")
    # cursor line
    _, w = box(54, 55, 66, 55)
    d.line([(54 * s, 55 * s), (66 * s, 55 * s)], fill=BLUE, width=w)
    # stand
    _, w = box(54, 64, 54, 70)
    d.line([(54 * s, 64 * s), (54 * s, 70 * s)], fill=BLUE, width=w)
    d.line([(45 * s, 71 * s), (63 * s, 71 * s)], fill=BLUE, width=w)

    img.save(path)

if __name__ == "__main__":
    base = r"D:\Program Files\.zcode\workspace\default\Zrmt\app\src\main\res"
    for dpi, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                    ("xxhdpi", 144), ("xxxhdpi", 192)]:
        draw_icon(px, rf"{base}\mipmap-{dpi}\ic_launcher.png")
        draw_icon(px, rf"{base}\mipmap-{dpi}\ic_launcher_round.png")
    print("icons generated")
