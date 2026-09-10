# -*- coding: utf-8 -*-
'''surface sink 按需扫描工具（P2 减负版：无常驻守护、无注册表、零维护）。

定位：纯诊断工具。用来回答「全库现在有哪些 surface 写入者、分别落在什么形态上」，
不参与提交阻断，也不维护任何清单文件。

用法（仓库任意工作树）：
    python tools/audit/surface_sink_ownership.py --summary
    python tools/audit/surface_sink_ownership.py --summary --category review-required
    python tools/audit/surface_sink_ownership.py --summary --sinks 20

判类为启发式初判，仅供人工阅读：
    binder            —— 该节点由 SceneSurfaceBinder 独占表面（同文件可见 bind 调用）
    binder-delegated  —— 节点静态设底 + 绑定通道
    designed-static   —— 设计上的一次性静态底色（不进主题通道）
    light-slot        —— 元素级轻量通道：caret/thumb/dot/scrim/行覆写等独占子节点
    compat-exempt     —— 兼容入口本体（SceneControlChrome / SceneStateColors / SceneChromeTokens）
    review-required   —— 启发式未命中，建议人工瞄一眼
'''

import argparse
import io
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from repo_paths import MAIN_JAVA  # noqa: E402

SINKS = ('setBackgroundColor', 'setBorderColor')
CALL_RE = re.compile(r'([A-Za-z_$][A-Za-z0-9_$.]*)\s*\.\s*(setBackgroundColor|setBorderColor)\s*\(')
MREF_RE = re.compile(r'([A-Za-z_$][A-Za-z0-9_$.]*)\s*::\s*(setBackgroundColor|setBorderColor)\b')
LIGHT_SLOT_TOKENS = ('caret', 'thumb', 'dot', 'scrim', 'bar', 'Bar', 'indicator', 'badge', 'icon', 'chip',
                     'highlight', 'separator', 'fill', 'track', 'handle', 'row', 'cell', 'line', 'slot',
                     'marker', 'node')


def java_files():
    for dirpath, _dirnames, filenames in os.walk(str(MAIN_JAVA)):
        for name in sorted(filenames):
            if name.endswith('.java'):
                yield Path(dirpath) / name


def binder_nodes(text):
    names = set()
    for match in re.finditer(r'SceneSurfaceBinder\.bind\(\s*[A-Za-z_$][A-Za-z0-9_$.]*\s*,\s*([A-Za-z_$][A-Za-z0-9_$]*)', text):
        names.add(match.group(1))
    for match in re.finditer(r'binder\.bind\(\s*[A-Za-z_$][A-Za-z0-9_$.]*\s*,\s*([A-Za-z_$][A-Za-z0-9_$]*)', text):
        names.add(match.group(1))
    return names


def classify(relative, owner, bound_names):
    if ('ui/scene/control/SceneControlChrome.java' in relative
            or 'ui/scene/paint/SceneStateColors' in relative
            or 'SceneChromeTokens' in relative):
        return 'compat-exempt'
    if 'ui/scene/theme/SceneSurfaceBinder.java' in relative:
        return 'binder'
    if owner and owner in bound_names:
        return 'binder'
    if any(token in owner for token in LIGHT_SLOT_TOKENS):
        return 'light-slot'
    return 'review-required'


def scan():
    entries = []
    bound_by_file = {}
    for path in java_files():
        text = io.open(str(path), encoding='utf-8', errors='replace').read()
        relative = path.relative_to(MAIN_JAVA).as_posix()
        bound_by_file[relative] = binder_nodes(text)
        lines = text.splitlines()
        for index, line in enumerate(lines):
            if not any(sink in line for sink in SINKS):
                continue
            stripped = line.strip()
            if stripped.startswith('*') or stripped.startswith('//') or stripped.startswith('/*'):
                continue
            matches = list(CALL_RE.finditer(line))
            if matches:
                match = matches[-1]
                owner, property_name = match.group(1), match.group(2)
            else:
                match = MREF_RE.search(line)
                if match is None:
                    continue
                owner, property_name = match.group(1), match.group(2)
            entries.append({'file': relative, 'owner': owner, 'property': property_name,
                            'line': index + 1, 'snippet': stripped[:100]})
    merged = {}
    for entry in entries:
        key = (entry['file'], entry['owner'], entry['property'])
        if key in merged:
            merged[key]['lines'].append(entry['line'])
        else:
            merged[key] = dict(entry, lines=[entry['line']])
    registry = []
    for key in sorted(merged):
        entry = merged[key]
        registry.append({
            'file': entry['file'],
            'owner': entry['owner'],
            'property': entry['property'],
            'category': classify(entry['file'], entry['owner'], bound_by_file[entry['file']]),
            'lines': sorted(entry['lines']),
            'note': entry['snippet'],
        })
    return registry


def main():
    parser = argparse.ArgumentParser(description='surface sink 按需扫描（诊断工具，不阻断）')
    parser.add_argument('--summary', action='store_true')
    parser.add_argument('--sinks', type=int, default=0)
    parser.add_argument('--category', default=None)
    args = parser.parse_args()
    registry = scan()
    counts = {}
    for entry in registry:
        counts[entry['category']] = counts.get(entry['category'], 0) + 1
    print('== surface sink 扫描（src/main，诊断用，无清单） ==')
    print('条目=%d | 写入行=%d' % (len(registry), sum(len(e['lines']) for e in registry)))
    for category in sorted(counts, key=lambda c: -counts[c]):
        print('  %-18s %d' % (category, counts[category]))
    show = [e for e in registry if args.category is None or e['category'] == args.category]
    if args.sinks:
        show = show[:args.sinks]
    for entry in show:
        print('  [%s] %s | %s.%s | L%s | %s' % (entry['category'], entry['file'], entry['owner'],
                                                 entry['property'], entry['lines'], entry['note'][:70]))
    return 0


if __name__ == '__main__':
    sys.exit(main())