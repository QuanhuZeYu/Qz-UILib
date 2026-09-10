# -*- coding: utf-8 -*-
# v2.5（2026-09-10 重审修）：补上此前缺脚本溯源的 onAccent-over-selBg 行（v2.4 §7 手抄缺口）
"""审计包附录 A v2.1：所有表格行由本脚本直接输出（禁手抄），判定用未舍入值。
合成规则声明：src-over 在编码 sRGB 通道线性插值后四舍五入取整（模拟渲染器逐通道混合），
亮度计算使用取整后的整数 RGB。"""
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

def lin(c):
    c /= 255.0
    return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

def lum(v):
    return 0.2126 * lin((v >> 16) & 255) + 0.7152 * lin((v >> 8) & 255) + 0.0722 * lin(v & 255)

def cr(a, b):
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)

def over(fg, bg):  # fg 带 alpha 的 ARGB over bg RGB(int)
    a = ((fg >> 24) & 0xFF) / 255.0
    r = round(((fg >> 16) & 255) * a + ((bg >> 16) & 255) * (1 - a))
    g = round(((fg >> 8) & 255) * a + ((bg >> 8) & 255) * (1 - a))
    b = round((fg & 255) * a + (bg & 255) * (1 - a))
    return (r << 16) | (g << 8) | b

FALLBACK = 0x2B2930
PANEL_D = over(0x14EAF7FF, FALLBACK)
PANEL_L = over(0x33FFFFFF, FALLBACK)
print("M1 dark底 #%06X (L=%.6f)  light底 #%06X (L=%.6f)" % (PANEL_D, lum(PANEL_D), PANEL_L, lum(PANEL_L)))

print("\n== success 候选域（M1 双档，判定未舍入≥4.5） ==")
for g in [0x00E676, 0xA5D6A7, 0x66BB6A, 0x4CAF50, 0x006C34]:
    d, l = cr(g | 0xFF000000, PANEL_D | 0xFF000000), cr(g | 0xFF000000, PANEL_L | 0xFF000000)
    print("#%06X  dark=%.6f light=%.6f  both_pass=%s  (vs纯白=%.2f)" %
          (g, d, l, d >= 4.5 and l >= 4.5, cr(g | 0xFF000000, 0xFFFFFFFF)))

print("\n== 虚拟化轻量档（dark 档，叠于 M1 dark 底） ==")
idle = PANEL_D
hover = over(0x1FFFFFFF, idle)
sel = over(0x592A1E68, idle)
print("idle #%06X L=%.6f | hover #%06X L=%.6f | selected #%06X L=%.6f" %
      (idle, lum(idle), hover, lum(hover), sel, lum(sel)))
print("CR sel/idle=%.6f  CR sel/hover=%.6f  CR hover/idle=%.6f" %
      (cr(sel | 0xFF000000, idle | 0xFF000000), cr(sel | 0xFF000000, hover | 0xFF000000),
       cr(hover | 0xFF000000, idle | 0xFF000000)))
print("ΔL%%(sel-idle)/idle=%+.1f  (sel-hover)/hover=%+.1f  (hover-idle)/idle=%+.1f" %
      (100 * (lum(sel) - lum(idle)) / lum(idle), 100 * (lum(sel) - lum(hover)) / lum(hover),
       100 * (lum(hover) - lum(idle)) / lum(idle)))
print("fg(0xFFE6E1E5) on hover CR=%.2f  on selected CR=%.2f" %
      (cr(0xFFE6E1E5, hover | 0xFF000000), cr(0xFFE6E1E5, sel | 0xFF000000)))

print("\n== 主表复算（与 v2 已发布值对照） ==")
main = [("fg dark", 0xFFE6E1E5, PANEL_D), ("fg light", 0xFF1C1B1F, PANEL_L),
        ("muted dark", 0xFFCAC4D0, PANEL_D), ("muted light", 0xFF49454F, PANEL_L),
        ("errorText dark", 0xFFFFB4AB, PANEL_D), ("errorText light", 0xFF8C1D18, PANEL_L),
        ("warningText dark", 0xFFFBBF24, PANEL_D), ("warningText light", 0xFF8B5000, PANEL_L),
        ("danger dark", 0xFF7F1D1D, PANEL_D), ("danger light", 0xFFB3261E, PANEL_L),
        ("disabled dark", 0xFF79747E, PANEL_D), ("disabled light", 0xFF9E9AA7, PANEL_L),
        ("accent dark", 0xFF4F378B, PANEL_D), ("accent light", 0xFF6750A4, PANEL_L)]
for n, f, b in main:
    print("%-18s %.2f" % (n, cr(f, b | 0xFF000000)))

print("\n== M2 包络 ==")
for w, n in ((0xFFFFFF, "纯白"), (0x000000, "纯黑")):
    print("%s: darkPANEL=%s fg=%.2f | lightPANEL=%s fg=%.2f" %
          (n, "#%06X" % over(0x14EAF7FF, w), cr(0xFFE6E1E5, over(0x14EAF7FF, w) | 0xFF000000),
           "#%06X" % over(0x33FFFFFF, w), cr(0xFF1C1B1F, over(0x33FFFFFF, w) | 0xFF000000)))

print("\n== onAccent-over-selBg（v2.5 补入：底为选中底色；dark onAccent 0xFFEADDFF / light selFg 0xFFFFFFFF） ==")
print("dark  selBg #%06X  CR=%.4f  (文档 v2.4 记 7.23)" % (0x4F378B, cr(0xFFEADDFF, 0xFF4F378B)))
print("light selBg #%06X  CR=%.4f  (文档 v2.4 记 6.44)" % (0x6750A4, cr(0xFFFFFFFF, 0xFF6750A4)))
