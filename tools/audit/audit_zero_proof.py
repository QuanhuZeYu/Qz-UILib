# -*- coding: utf-8 -*-
"""Legacy producer 三层零点核验（审查者A L1/L2/L3 口径，输出直接嵌入审计包，禁手抄）。
L1 调用点；L2 全引用（import/方法引用/字符串反射）；L3 语义生产点（selectable-background
属性 setBackgroundColor/setBorderColor 在 src/main 的全部出现及归属分类）。"""
import io
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = r"D:\Code\MC\Qz-UILib\src\main\java"

TARGETS = ["bindSelectableBackground", "bindStandardBorder", "dangerBackground",
           "selectedBackground", "standardBackground", "inputBackground",
           "listItemBackground", "applyPanelChrome", "applyOuterShell", "asFormTheme"]

def walk():
    for dp, _dn, fn in os.walk(ROOT):
        for f in fn:
            if f.endswith(".java"):
                p = os.path.join(dp, f)
                yield p, io.open(p, encoding="utf-8").read()

files = list(walk())
print("== L2 全引用核验（含 L1 调用；模式：标识符独立词，任何出现位置，排除定义体自身） ==")
for t in TARGETS:
    hits = []
    for p, src in files:
        for i, line in enumerate(src.splitlines(), 1):
            if re.search(r"\b" + t + r"\b", line):
                rel = os.path.relpath(p, ROOT)
                kind = "DEF" if (re.search(r"(public|private|static).*\b" + t + r"\s*\(", line)
                                 or re.search(r"\b" + t + r"\s*\(", line) and "enum" in line) else ""
                # 定义体 = 方法声明行；测试 infra 不在 src/main
                code = line.strip()
                is_comment = code.startswith("*") or code.startswith("//") or code.startswith("/*")
                is_def = bool(re.search(r"\b" + t + r"\s*\(", line)) and bool(
                    re.search(r"(public|private|protected)[^{]*\b" + t + r"\s*\(", line))
                tag = "DEF-DECL" if is_def else ("COMMENT" if is_comment else "REF")
                hits.append((rel, i, tag, code[:110]))
    refs = [h for h in hits if h[2] == "REF"]
    print("\n%s: 总出现=%d 定义=%d 注释=%d **REF(真实引用)=%d**" % (
        t, len(hits), sum(1 for h in hits if h[2] == "DEF-DECL"),
        sum(1 for h in hits if h[2] == "COMMENT"), len(refs)))
    for h in refs:
        print("   REF:", h[0] + ":" + str(h[1]), h[3])

print("\n== L3 语义生产点：src/main 全部 setBackgroundColor 出现分类 ==")
sink_files = {}
for p, src in files:
    for i, line in enumerate(src.splitlines(), 1):
        if "setBackgroundColor" in line:
            rel = os.path.relpath(p, ROOT)
            sink_files.setdefault(rel, []).append((i, line.strip()[:100]))
for rel, rows in sorted(sink_files.items()):
    print("%-78s %d 处" % (rel, len(rows)))

print("\n== 同样扫 setBorderColor（selectable 边框语义） ==")
cnt = {}
for p, src in files:
    for i, line in enumerate(src.splitlines(), 1):
        if "setBorderColor" in line:
            cnt.setdefault(os.path.relpath(p, ROOT), 0)
            cnt[os.path.relpath(p, ROOT)] += 1
for rel, n in sorted(cnt.items()):
    print("%-78s %d 处" % (rel, n))
