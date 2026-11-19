"""Draws the pictures on Dayline's empty pages and writes them for both apps.

Small still lifes drawn as clean outlines with rounded ends, on a 280 × 200 canvas. Each object first
erases what lies behind it (a cut, "x"), so overlapping outlines stay tidy and the page shows through.

  python3 tools/empty-art/gen.py          previews in tools/empty-art/out (SVG, light and dark)
  python3 tools/empty-art/gen.py --code   writes macos/.../EmptyArtData.swift and app/.../EmptyArtData.kt
"""
import os, sys
import math
def f(v): 
    s = f"{v:.1f}"
    return s[:-2] if s.endswith('.0') else s
def P(x, y): return f"{f(x)} {f(y)}"

def smooth(pts, closed=True, t=1.0):
    """Catmull-Rom through points -> cubic path."""
    n = len(pts)
    d = f"M{P(*pts[0])}"
    rng = range(n) if closed else range(n - 1)
    for i in rng:
        p0 = pts[(i - 1) % n] if (closed or i > 0) else pts[i]
        p1 = pts[i]; p2 = pts[(i + 1) % n]
        p3 = pts[(i + 2) % n] if (closed or i + 2 < n) else pts[(i + 1) % n]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6 * t, p1[1] + (p2[1] - p0[1]) / 6 * t)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6 * t, p2[1] - (p3[1] - p1[1]) / 6 * t)
        d += f"C{P(*c1)} {P(*c2)} {P(*p2)}"
    return d + ("Z" if closed else "")

def arcseg(cx, cy, r, a0, a1, rx=None):
    """Cubic segments (no move) for an arc from a0 to a1 degrees (y down)."""
    ry = r; rx = rx or r
    out = ""; n = max(1, math.ceil(abs(a1 - a0) / 90))
    da = (a1 - a0) / n
    for i in range(n):
        t0 = math.radians(a0 + i * da); t1 = math.radians(a0 + (i + 1) * da)
        k = 4 / 3 * math.tan((t1 - t0) / 4)
        p0 = (cx + rx * math.cos(t0), cy + ry * math.sin(t0)); p3 = (cx + rx * math.cos(t1), cy + ry * math.sin(t1))
        c1 = (p0[0] - k * rx * math.sin(t0), p0[1] + k * ry * math.cos(t0))
        c2 = (p3[0] + k * rx * math.sin(t1), p3[1] - k * ry * math.cos(t1))
        out += f"C{P(*c1)} {P(*c2)} {P(*p3)}"
    return out

def arc(cx, cy, r, a0, a1, rx=None):
    rx_ = rx or r
    return f"M{P(cx + rx_ * math.cos(math.radians(a0)), cy + r * math.sin(math.radians(a0)))}" + arcseg(cx, cy, r, a0, a1, rx)

def ellipse(cx, cy, rx, ry): return arc(cx, cy, ry, 0, 360, rx) + "Z"

def poly(pts, closed=False): return "M" + "L".join(P(*p) for p in pts) + ("Z" if closed else "")

def rrect(x0, y0, x1, y1, r):
    return (f"M{P(x0 + r, y0)}L{P(x1 - r, y0)}" + arcseg(x1 - r, y0 + r, r, 270, 360) + f"L{P(x1, y1 - r)}" + arcseg(x1 - r, y1 - r, r, 0, 90)
            + f"L{P(x0 + r, y1)}" + arcseg(x0 + r, y1 - r, r, 90, 180) + f"L{P(x0, y0 + r)}" + arcseg(x0 + r, y0 + r, r, 180, 270) + "Z")

# element: (d, fill, stroke, width)

def rot(pts, cx, cy, deg):
    a = math.radians(deg); c, s = math.cos(a), math.sin(a)
    return [(cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c) for x, y in pts]

def star(cx, cy, r, k=0.28):
    """Four-point sparkle."""
    pts = []
    for i in range(8):
        a = math.radians(-90 + i * 45); rr = r if i % 2 == 0 else r * k
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return smooth(pts, t=0.25)


W, H, GY = 280, 200, 188
OUT, DET = 3.2, 2.4   # outline and detail line widths

class Art:
    """Line art: every object is an outline. Drawing an object first erases what lies behind it
    (a cut in its shape), so overlapping outlines stay clean and the page shows through."""
    def __init__(self): self.el = []
    def add(self, d, fill=None, stroke=None, w=0): self.el.append((d, fill, stroke, w)); return self
    def obj(self, d, w=3.2, tint=False):
        """An outlined object; `tint` fills it with the theme colour."""
        self.add(d, "cut")
        if tint: self.add(d, "accent")
        return self.add(d, None, "ink", w)
    def line(self, d, w=2.4): return self.add(d, None, "ink", w)
    def erase(self, d, w=None): return self.add(d, None if w else "cut", "cut" if w else None, w or 0)

OUT, DET = 3.2, 2.2

def ground(a, x0=18, x1=262, gaps=()):
    xs = [x0] + [g for gap in gaps for g in gap] + [x1]
    for i in range(0, len(xs), 2): a.line(poly([(xs[i], GY), (xs[i + 1], GY)]), 2.6)

def cloud(a, x, y, w=1.0):
    d = f"M{P(x - 24 * w, y)}L{P(x - 15 * w, y)}" + arcseg(x - 8 * w, y, 7 * w, 180, 360)
    d += f"L{P(x - 0.5 * w, y)}" + arcseg(x + 8 * w, y - 1 * w, 9 * w, 195, 345) + f"L{P(x + 17 * w, y)}" + arcseg(x + 22 * w, y, 5 * w, 180, 360) + f"L{P(x + 34 * w, y)}"
    a.line(d, 2)

def sparkle(a, x, y, r): a.line(star(x, y, r, 0.3), 2)

def birds(a, pts):
    for x, y, s in pts:
        a.line(f"M{P(x - 9*s, y + 2*s)}C{P(x - 7*s, y - 4*s)} {P(x - 2*s, y - 4*s)} {P(x, y + 1*s)}C{P(x + 2*s, y - 4*s)} {P(x + 7*s, y - 4*s)} {P(x + 9*s, y + 2*s)}", 2)

def sun(a, cx, cy, r, rays=8, r0=None, r1=None, start=0):
    a.obj(ellipse(cx, cy, r, r), 2.8)
    r0 = r0 or r + 6; r1 = r1 or r + 15
    for i in range(rays):
        t = math.radians(start + i * 360 / rays)
        a.line(poly([(cx + r0 * math.cos(t), cy + r0 * math.sin(t)), (cx + r1 * math.cos(t), cy + r1 * math.sin(t))]), 2.4)

def moon(a, cx, cy, r, k=0.82, tint=False):
    """Crescent: the outer circle's arc and the inner bite's arc."""
    ox, oy, rr = cx + r * 0.42, cy - r * 0.34, r * k
    # Where the two circles meet.
    dx, dy = ox - cx, oy - cy; d = math.hypot(dx, dy)
    aa = (r * r - rr * rr + d * d) / (2 * d); h = math.sqrt(max(0, r * r - aa * aa))
    mx, my = cx + aa * dx / d, cy + aa * dy / d
    p1 = (mx + h * dy / d, my - h * dx / d); p2 = (mx - h * dy / d, my + h * dx / d)
    ang = lambda c, p: math.degrees(math.atan2(p[1] - c[1], p[0] - c[0]))
    a1, a2 = ang((cx, cy), p1), ang((cx, cy), p2)
    b1, b2 = ang((ox, oy), p1), ang((ox, oy), p2)
    if a2 < a1: a2 += 360
    outer = arcseg(cx, cy, r, a1, a2) if (a2 - a1) > 180 else arcseg(cx, cy, r, a2, a1 + 360)
    start = p1 if (a2 - a1) > 180 else p2
    end = p2 if start == p1 else p1
    bs, be = (b2, b1) if start == p1 else (b1, b2)
    if be > bs: be -= 360
    pts = []
    t0, t1 = (a1, a2) if (a2 - a1) > 180 else (a2, a1 + 360)
    for i in range(25): t = math.radians(t0 + (t1 - t0) * i / 24); pts.append((cx + r * math.cos(t), cy + r * math.sin(t)))
    e = pts[-1]; s_ = pts[0]
    u0, u1 = ang((ox, oy), e), ang((ox, oy), s_)
    if u1 > u0: u1 -= 360
    for i in range(1, 24): t = math.radians(u0 + (u1 - u0) * i / 24); pts.append((ox + rr * math.cos(t), oy + rr * math.sin(t)))
    a.obj(smooth(pts, t=0.5), 3, tint)

def leaf(a, x0, y0, x1, y1, bulge=0.36, veins=3):
    dx, dy = x1 - x0, y1 - y0; n = math.hypot(dx, dy); px, py = -dy / n, dx / n
    b = n * bulge
    c1 = (x0 + dx * 0.25 + px * b, y0 + dy * 0.25 + py * b); c2 = (x0 + dx * 0.8 + px * b * 0.7, y0 + dy * 0.8 + py * b * 0.7)
    c3 = (x0 + dx * 0.8 - px * b * 0.7, y0 + dy * 0.8 - py * b * 0.7); c4 = (x0 + dx * 0.25 - px * b, y0 + dy * 0.25 - py * b)
    a.obj(f"M{P(x0, y0)}C{P(*c1)} {P(*c2)} {P(x1, y1)}C{P(*c3)} {P(*c4)} {P(x0, y0)}Z", 2.8)
    a.line(poly([(x0 + dx * 0.05, y0 + dy * 0.05), (x0 + dx * 0.84, y0 + dy * 0.84)]), 1.8)
    for i in range(veins):
        t = 0.3 + i * 0.17; s = 1 if i % 2 == 0 else -1
        bx, by = x0 + dx * t, y0 + dy * t
        a.line(poly([(bx, by), (bx + dx * 0.12 + s * px * b * 0.5, by + dy * 0.12 + s * py * b * 0.5)]), 1.6)

def feather(a, x0, y0, x1, y1):
    a.line(poly([(x0 - (x1 - x0) * 0.16, y0 - (y1 - y0) * 0.16), (x0, y0)]), 2.6)
    leaf(a, x0, y0, x1, y1, 0.3, 4)

def cairn(a, cx, base, sizes):
    y = base; shapes = []
    for rx, ry, dx in sizes: shapes.append((cx + dx, y - ry, rx, ry)); y -= ry * 2 - 3
    for x, yy, rx, ry in shapes: a.obj(ellipse(x, yy, rx, ry), 3)

def pencil(a, cx, cy, ang, length=150, w=24):
    x0, x1 = cx - w / 2, cx + w / 2; top = cy - length / 2; bot = cy + length / 2
    R = lambda pts: rot(pts, cx, cy, ang)
    a.obj(smooth(R([(x0, top + 30), (x1, top + 30), (x1, bot - 8), (x1 - 2, bot), (x0 + 2, bot), (x0, bot - 8)]), t=0.15), 3, tint=True)
    a.obj(smooth(R([(x0, top + 30), (cx, top), (x1, top + 30)]), t=0.08) , 3)
    a.line(poly(R([(cx - 4.5, top + 9), (cx, top + 1), (cx + 4.5, top + 9)])), 2.2)
    for dx in (-w / 6, w / 6): a.line(poly(R([(cx + dx, top + 38), (cx + dx, bot - 18)])), 1.8)
    a.line(poly(R([(x0 + 2, bot - 14), (x1 - 2, bot - 14)])), 2)

def bulb(a, cx, cy, r, tint=True):
    glass = smooth([(cx, cy - r), (cx + r * 0.72, cy - r * 0.72), (cx + r, cy), (cx + r * 0.72, cy + r * 0.72),
                    (cx + r * 0.42, cy + r * 1.16), (cx - r * 0.42, cy + r * 1.16), (cx - r * 0.72, cy + r * 0.72), (cx - r, cy), (cx - r * 0.72, cy - r * 0.72)])
    by = cy + r * 1.18
    a.obj(rrect(cx - r * 0.44, by, cx + r * 0.44, by + r * 0.62, r * 0.14), 3)
    for i in (1, 2): a.line(poly([(cx - r * 0.44, by + i * r * 0.2), (cx + r * 0.44, by + i * r * 0.2)]), 1.8)
    a.obj(glass, 3.2, tint)
    for dx in (-1, 1):
        a.line(ellipse(cx + dx * r * 0.18, cy + r * 0.2, r * 0.14, r * 0.14), 2)
        a.line(poly([(cx + dx * r * 0.05, cy + r * 0.33), (cx + dx * r * 0.05, cy + r * 1.16)]), 2)
    a.line(arc(cx, cy, r * 0.72, 205, 250), 2.2)

def balloon(a, x, y, r, tint=True):
    a.line(f"M{P(x, y + r * 1.32)}C{P(x - 8, y + r * 1.8)} {P(x + 8, y + r * 2.2)} {P(x - 2, GY - 2)}", 2)
    a.obj(poly([(x - 5, y + r * 1.34), (x + 5, y + r * 1.34), (x, y + r * 1.18)], True), 2.4)
    a.obj(smooth([(x, y - r), (x + r * 0.76, y - r * 0.6), (x + r, y + r * 0.1), (x + r * 0.66, y + r * 0.86), (x, y + r * 1.2), (x - r * 0.66, y + r * 0.86), (x - r, y + r * 0.1), (x - r * 0.76, y - r * 0.6)]), 3.2, tint)
    a.line(arc(x, y + r * 0.05, r * 0.66, 200, 250), 2.2)

# ---- scenes (280 × 200) -------------------------------------------------
def notebook():
    a = Art(); ground(a); cloud(a, 236, 40, 0.9); sparkle(a, 44, 36, 8)
    pad = rot([(86, 46), (184, 46), (184, GY), (86, GY)], 135, 114, -5)
    a.obj(smooth(pad, t=0.12), 3.2)
    for i in range(6):
        p0 = rot([(100 + i * 15, 36)], 135, 114, -5)[0]
        a.obj(rrect(p0[0] - 3, p0[1], p0[0] + 3, p0[1] + 20, 3), 2.2)
    for y, x1 in [(78, 168), (96, 160), (114, 170), (132, 150), (150, 162)]:
        a.line(poly(rot([(102, y), (x1, y)], 135, 114, -5)), 2.4)
    pencil(a, 198, 118, 18, 150, 24)
    a.obj(f"M{P(24, 138)}L{P(78, 138)}L{P(74, 168)}C{P(72, 178)} {P(64, GY)} {P(51, GY)}C{P(38, GY)} {P(30, 178)} {P(28, 168)}Z", 3.2)
    a.line(arc(77, 152, 10, 280, 440), 3)
    for x in (42, 58): a.line(f"M{P(x, 128)}C{P(x - 7, 118)} {P(x + 7, 110)} {P(x, 98)}", 2.2)
    return a

def journal():
    a = Art(); ground(a)
    moon(a, 190, 58, 36, tint=True)
    for x, y, r in [(124, 30, 9), (248, 42, 7), (104, 60, 5)]: sparkle(a, x, y, r)
    cx = 128
    for s in (-1, 1):
        x = lambda v: cx + s * v
        a.obj(f"M{P(cx, 100)}C{P(x(26), 86)} {P(x(60), 86)} {P(x(92), 94)}L{P(x(92), 178)}C{P(x(60), 170)} {P(x(26), 170)} {P(cx, 182)}Z", 3.2)
        for i in range(4):
            y = 116 + i * 15
            a.line(f"M{P(x(14), y + 4)}C{P(x(30), y - 3)} {P(x(54), y - 3)} {P(x(76), y)}", 2)
    a.line(poly([(cx, 100), (cx, 182)]), 2.6)
    feather(a, 208, 174, 254, 92)
    return a

def inbox():
    a = Art(); ground(a); cloud(a, 52, 48, 1.0); sparkle(a, 150, 34, 7)
    cairn(a, 86, GY + 1, [(42, 15, 0), (31, 13, 4), (22, 11, -2), (13, 9, 2)])
    wing1 = [(122, 98), (252, 28), (186, 114)]
    wing2 = [(186, 114), (252, 28), (214, 150)]
    a.obj(smooth(wing1, t=0.04), 3.2); a.obj(smooth(wing2, t=0.04), 3.2, tint=True)
    a.obj(smooth([(186, 114), (192, 142), (204, 130)], t=0.05), 2.6)
    for x, y, r in [(152, 128, 3.6), (136, 140, 3), (122, 150, 2.4)]: a.line(ellipse(x, y, r, r), 1.8)
    return a

def calendar():
    a = Art(); ground(a); sun(a, 42, 46, 13, rays=8, start=22, r0=19, r1=28); cloud(a, 250, 32, 0.7)
    a.obj(rrect(78, 54, 192, GY, 14), 3.2)
    a.line(poly([(78, 90), (192, 90)]), 2.6)
    for x in (108, 162): a.obj(rrect(x - 6, 38, x + 6, 70, 6), 2.8)
    for r in range(4):
        for c in range(5):
            x, y = 96 + c * 19.5, 108 + r * 19
            if r == 3 and c > 2: continue
            if (r, c) == (2, 3): a.obj(ellipse(x, y, 9, 9), 2.6, tint=True)
            else: a.line(ellipse(x, y, 2.6, 2.6), 1.8)
    cx, cy = 220, 146
    for s in (-1, 1): a.obj(ellipse(cx + s * 24, cy - 33, 11, 11), 2.8, tint=True)
    for s in (-1, 1): a.line(poly([(cx + s * 22, cy + 32), (cx + s * 29, cy + 34 + 6)]), 3)
    a.obj(ellipse(cx, cy, 38, 38), 3.2)
    a.line(ellipse(cx, cy, 30, 30), 1.8)
    a.line(poly([(cx, cy), (cx, cy - 20)]), 3); a.line(poly([(cx, cy), (cx + 14, cy + 8)]), 3)
    return a

def checklist():
    a = Art(); ground(a); cloud(a, 54, 34, 0.9)
    balloon(a, 226, 62, 30)
    a.obj(ellipse(118, 112, 66, 66), 3.4)
    a.line(ellipse(118, 112, 54, 54), 1.8)
    a.line(poly([(86, 114), (108, 136), (152, 90)]), 9)
    for x, y, r in [(36, 70, 9), (190, 150, 6), (184, 30, 6)]: sparkle(a, x, y, r)
    return a

def ring_pts(cx, cy, rx, ry, deg, t0, t1, n=40):
    out = []
    for i in range(n + 1):
        t = math.radians(t0 + (t1 - t0) * i / n)
        out.append((cx + rx * math.cos(t), cy + ry * math.sin(t)))
    return rot(out, cx, cy, deg)

def star5(cx, cy, r, k=0.45):
    pts = []
    for i in range(10):
        t = math.radians(-90 + i * 36); rr = r if i % 2 == 0 else r * k
        pts.append((cx + rr * math.cos(t), cy + rr * math.sin(t)))
    return poly(pts, True)

def trophy():
    a = Art(); ground(a); cloud(a, 236, 40, 0.9)
    for x, y, r in [(52, 52, 9), (226, 92, 7), (80, 30, 5)]: sparkle(a, x, y, r)
    for x0, y0, x1, y1 in [(46, 112, 58, 104), (62, 136, 54, 146), (224, 132, 236, 126), (214, 150, 226, 156)]:
        a.line(poly([(x0, y0), (x1, y1)]), 2.6)
    cx = 140
    for s in (-1, 1): a.line(f"M{P(cx + s * 38, 66)}C{P(cx + s * 66, 66)} {P(cx + s * 64, 108)} {P(cx + s * 28, 112)}", 3.2)
    a.obj(f"M{P(cx - 44, 50)}L{P(cx + 44, 50)}L{P(cx + 40, 92)}C{P(cx + 36, 118)} {P(cx + 18, 132)} {P(cx, 132)}C{P(cx - 18, 132)} {P(cx - 36, 118)} {P(cx - 40, 92)}Z", 3.2, tint=True)
    a.obj(star5(cx, 86, 15), 2.4)
    a.obj(rrect(cx - 8, 130, cx + 8, 152, 3), 3)
    a.obj(rrect(cx - 30, 150, cx + 30, 164, 4), 3)
    a.obj(rrect(cx - 42, 164, cx + 42, GY, 5), 3)
    return a

def search():
    a = Art(); ground(a); cloud(a, 236, 34, 0.9)
    for x, y, r in [(36, 40, 9), (60, 70, 5), (250, 80, 6)]: sparkle(a, x, y, r)
    cx, cy = 140, 104
    # handle, then the rim on top of it
    a.obj(smooth([(174, 150), (186, 138), (228, 180), (216, 192)], t=0.12), 3.2)
    a.add(arc(cx, cy, 60, 0, 360) + "Z" + arc(cx, cy, 47, 360, 0) + "Z", "cut")
    a.line(ellipse(cx, cy, 60, 60), 3.2); a.line(ellipse(cx, cy, 47, 47), 3.2)
    # a little planet seen through the glass: back of its ring, the planet, front of its ring
    a.line(smooth(ring_pts(cx + 2, cy + 4, 34, 9, -18, 180, 360), closed=False), 2.4)
    a.obj(ellipse(cx + 2, cy + 4, 18, 18), 2.8, tint=True)
    a.line(smooth(ring_pts(cx + 2, cy + 4, 34, 9, -18, 0, 180), closed=False), 2.4)
    a.line(arc(cx, cy, 38, 205, 245), 2.6)
    return a

def sprout():
    a = Art(); ground(a); sun(a, 52, 44, 12, rays=8, start=22, r0=18, r1=26); cloud(a, 244, 36, 0.8)
    fx, fy = 176, 52
    a.line(f"M{P(fx, 128)}C{P(fx - 4, 102)} {P(fx + 4, 82)} {P(fx, fy + 12)}", 3)
    leaf(a, fx, 108, fx + 44, 82, 0.36, 3)
    leaf(a, fx, 116, fx - 42, 92, 0.36, 3)
    petals = [(fx + (22 + 11 * abs(math.cos(3 * t))) * math.cos(t), fy + (22 + 11 * abs(math.cos(3 * t))) * math.sin(t))
              for t in (math.radians(i * 5 + 30) for i in range(72))]
    a.obj(smooth(petals, t=0.6), 2.8, tint=True)
    a.obj(ellipse(fx, fy, 10, 10), 2.8)
    a.obj(smooth([(140, 138), (212, 138), (204, GY), (148, GY)], t=0.06), 3.2)
    a.obj(rrect(130, 124, 222, 144, 7), 3.2)
    a.obj(smooth([(24, 148), (78, 148), (74, GY), (28, GY)], t=0.06), 3.2)
    a.line(f"M{P(34, 148)}C{P(34, 120)} {P(68, 120)} {P(68, 148)}", 3.2)
    a.obj(smooth([(76, 172), (104, 142), (110, 146), (82, 180)], t=0.08), 2.8)
    a.obj(rrect(100, 134, 117, 144, 3), 2.6)
    for x, y in [(118, 154), (124, 166), (114, 170)]:
        a.line(f"M{P(x, y - 5)}C{P(x + 3, y)} {P(x + 3, y + 4)} {P(x, y + 4)}C{P(x - 3, y + 4)} {P(x - 3, y)} {P(x, y - 5)}Z", 1.8)
    return a

def chart():
    a = Art(); ground(a); cloud(a, 54, 40, 0.9)
    X = 12   # the bars, flag and line sit in the middle of the picture
    for x, y, r in [(256, 100, 7), (248, 44, 5)]: sparkle(a, x, y, r)
    for x0, x1, top in [(66 + X, 104 + X, 136), (116 + X, 154 + X, 102), (166 + X, 204 + X, 62)]:
        a.obj(f"M{P(x0, GY)}L{P(x0, top + 8)}" + arcseg(x0 + 8, top + 8, 8, 180, 270) + f"L{P(x1 - 8, top)}" + arcseg(x1 - 8, top + 8, 8, 270, 360) + f"L{P(x1, GY)}", 3.2, tint=x0 == 166 + X)
    a.line(poly([(185 + X, 62), (185 + X, 18)]), 3)
    a.obj(f"M{P(186 + X, 20)}C{P(198 + X, 14)} {P(206 + X, 28)} {P(222 + X, 22)}L{P(222 + X, 42)}C{P(206 + X, 48)} {P(198 + X, 34)} {P(186 + X, 40)}Z", 2.8, tint=True)
    a.line(poly([(40 + X, 132), (80 + X, 108), (106 + X, 116), (146 + X, 76)]), 2.6)
    a.line(poly([(132 + X, 76), (146 + X, 76), (146 + X, 90)]), 2.6)
    return a

def hello():
    a = Art(); ground(a); cloud(a, 56, 36, 0.9)
    for (x0, x1, y0, y1) in [(70, 186, 160, GY), (80, 176, 138, 160)]:
        a.obj(rrect(x0, y0, x1, y1, 4), 3)
        a.line(poly([(x0 + 12, y0 + (y1 - y0) / 2), (x1 - 22, y0 + (y1 - y0) / 2)]), 1.8)
        a.line(poly([(x1 - 14, y0 + 4), (x1 - 14, y1 - 4)]), 1.8)
    bulb(a, 128, 66, 34)
    for x, y, r in [(198, 36, 9), (60, 70, 6), (214, 70, 5)]: sparkle(a, x, y, r)
    moon(a, 230, 116, 22)
    cairn(a, 230, GY + 1, [(22, 9, 0), (16, 8, 3)])
    return a

SCENES = {"inbox": inbox, "calendar": calendar, "checklist": checklist, "trophy": trophy, "search": search, "sprout": sprout,
          "notebook": notebook, "journal": journal, "chart": chart, "hello": hello}

LIGHT = dict(ink="#1A1A1C", accent="#7C8CF8")
DARK = dict(ink="#EDEDF0", accent="#5C6BD6")
CODES = dict(ink="i", accent="a", cut="x")

def svg(art, pal, bg):
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W*2}" height="{H*2}"><rect width="100%" height="100%" fill="{bg}"/>']
    layer = []
    for i, (d, fl, st, w) in enumerate(art.el):
        if "cut" in (fl, st):
            layer = [f'<mask id="m{i}"><rect x="-50" y="-50" width="400" height="400" fill="white"/><path d="{d}" fill="{"black" if fl else "none"}" stroke="{"black" if st else "none"}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round"/></mask>', f'<g mask="url(#m{i})">'] + layer + ['</g>']
        else:
            layer.append(f'<path d="{d}" fill="{pal[fl] if fl else "none"}" stroke="{pal[st] if st else "none"}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round"/>')
    return "\n".join(out + layer + ["</svg>"])

def encode(art):
    return "|".join(f"{CODES[fl] if fl else '-'}{CODES[st] if st else '-'}{f(w)}:{d}" for d, fl, st, w in art.el)

HEADER = "Generated by tools/empty-art/gen.py — change the pictures there and run it again."

def write_code(repo):
    data = {k: encode(fn()) for k, fn in SCENES.items()}
    sw = [f"// {HEADER}", f"// Elements '<fill><stroke><width>:<path>' separated by '|' on a {W} × {H} canvas.",
          "// Roles: i = ink, a = the theme colour, x = cut (erases what is drawn before it), - = none.", "",
          "enum EmptyArtData {", f"    static let width: Double = {W}, height: Double = {H}",
          "    static let palette: [Character: (light: UInt32, dark: UInt32)] = ["]
    for role in ("ink",): sw.append(f'        "{CODES[role]}": (0x{LIGHT[role][1:]}, 0x{DARK[role][1:]}),')
    sw += ["    ]", "    static let scenes: [String: String] = ["] + [f'        "{k}": "{v}",' for k, v in data.items()] + ["    ]", "}", ""]
    open(f"{repo}/macos/Sources/Dayline/UI/Design/EmptyArtData.swift", "w").write("\n".join(sw))
    kt = [f"// {HEADER}", "package com.dayline.app.ui.components", "",
          f"/** Elements `<fill><stroke><width>:<path>` separated by `|` on a {W} × {H} canvas. Roles: i = ink, a = the theme colour, x = cut, - = none. */",
          "internal object EmptyArtData {", f"    const val WIDTH = {W}f", f"    const val HEIGHT = {H}f",
          "    /** Role → (light, dark) colour. */", "    val palette: Map<Char, Pair<Long, Long>> = mapOf("]
    for role in ("ink",): kt.append(f"        '{CODES[role]}' to (0xFF{LIGHT[role][1:]} to 0xFF{DARK[role][1:]}),")
    kt += ["    )", "    val scenes: Map<String, String> = mapOf("] + [f'        "{k}" to "{v}",' for k, v in data.items()] + ["    )", "}", ""]
    open(f"{repo}/app/src/main/java/com/dayline/app/ui/components/EmptyArtData.kt", "w").write("\n".join(kt))

if __name__ == "__main__":
    here = os.path.dirname(os.path.abspath(__file__))
    if len(sys.argv) > 1 and sys.argv[1] == "--code":
        write_code(sys.argv[2] if len(sys.argv) > 2 else os.path.join(here, "..", "..")); sys.exit()
    out = os.path.join(here, "out"); os.makedirs(out, exist_ok=True)
    for name, fn in SCENES.items():
        a = fn()
        open(os.path.join(out, f"{name}.svg"), "w").write(svg(a, LIGHT, "#F1F2F4"))
        open(os.path.join(out, f"{name}-dark.svg"), "w").write(svg(a, DARK, "#161618"))
