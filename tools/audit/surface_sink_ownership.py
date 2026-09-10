# -*- coding: utf-8 -*-
'''P2 surface sink 归属扫描器：枚举 src/main 的 surface 写入点并按类别给出初判。

用法：
    python tools/audit/surface_sink_ownership.py --emit      # 打印注册表 JSON（人工审查后落盘）
    python tools/audit/surface_sink_ownership.py --summary   # 分类统计 + 明细

类别（注册制口径，新 sink 必须先登记）：
    binder            —— 该节点由 SceneSurfaceBinder 独占表面（同文件/同函数可见 bind 调用）
    binder-delegated  —— 节点静态设底 + binder 绑定（binder 后写覆盖静态值）
    designed-static   —— 设计上的一次性静态底色（无 binder 参与）
    light-slot        —— 元素级轻量通道：caret/thumb/dot/scrim/行覆写等独占子节点
    compat-exempt     —— 兼容入口本体（SceneControlChrome / SceneStateColors / SceneChromeTokens）
    review-required   —— 扫描器无法判定，必须人工归类后才能进注册表
'''

import argparse
import io
import json
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from repo_paths import MAIN_JAVA, TEST_RESOURCES  # noqa: E402

SINKS = ('setBackgroundColor', 'setBorderColor')
CALL_RE = re.compile(r'([A-Za-z_$][A-Za-z0-9_$.]*)\s*\.\s*(setBackgroundColor|setBorderColor)\s*\(')
MREF_RE = re.compile(r'([A-Za-z_$][A-Za-z0-9_$.]*)\s*::\s*(setBackgroundColor|setBorderColor)\b')
# -*- coding: utf-8 -*-
'''P2 surface sink 归属扫描器：枚举 src/main 的 surface 写入点并按类别给出初判。

用法：
    python tools/audit/surface_sink_ownership.py --emit      # 打印注册表 JSON（人工审查后落盘）
    python tools/audit/surface_sink_ownership.py --summary   # 分类统计 + 明细

类别（注册制口径，新 sink 必须先登记）：
    binder            —— 该节点由 SceneSurfaceBinder 独占表面（同文件/同函数可见 bind 调用）
    binder-delegated  —— 节点静态设底 + binder 绑定（binder 后写覆盖静态值）
    designed-static   —— 设计上的一次性静态底色（无 binder 参与）
    light-slot        —— 元素级轻量通道：caret/thumb/dot/scrim/行覆写等独占子节点
    compat-exempt     —— 兼容入口本体（SceneControlChrome / SceneStateColors / SceneChromeTokens）
    review-required   —— 扫描器无法判定，必须人工归类后才能进注册表
'''

import argparse
import io
import json
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from repo_paths import MAIN_JAVA, TEST_RESOURCES  # noqa: E402

SINKS = ('setBackgroundColor', 'setBorderColor')
CALL_RE = re.compile(r'([A-Za-z_$][A-Za-z0-9_$.]*)\s*\.\s*(setBackgroundColor|setBorderColor)\s*\(')
MREF_RE = re.compile(r'([A-Za-z_$][A-Za-z0-9_$.]*)\s*::\s*(setBackgroundColor|setBorderColor)\b')
OWNER_PATTERNS = (
    re.compile(r'(?:for|foreach)\s*\(\s*(?:final\s+)?SceneNode\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*:'),
    re.compile(r'SceneNode\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*[=;)]'),
    re.compile(r'([A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*new\s+SceneNode'),
    re.compile(r'([A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*SceneNode\.[A-Za-z]+'),
    re.compile(r'([A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*sc\.[A-Za-z]+\('),
    re.compile(r'([A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*Scene[A-Za-z]+\.create'),
    re.compile(r'([A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*[A-Za-z_$][A-Za-z0-9_$.]*\.(?:label|icon|text)\('),
    re.compile(r'\(\s*(?:final\s+)?SceneNode\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*[,)]'),
)
LIGHT_SLOT_TOKENS = ('caret', 'thumb', 'dot', 'scrim', 'bar', 'Bar', 'indicator', 'badge', 'icon', 'chip',
                     'highlight', 'separator', 'fill', 'track', 'handle', 'row', 'cell', 'line', 'slot',
                     'marker', 'node')


def java_files():
    for dirpath, _dirnames, filenames in os.walk(str(MAIN_JAVA)):
        for name in sorted(filenames):
            if name.endswith('.java'):
                yield Path(dirpath) / name





def binder_nodes(text):
    '''同文件出现过的 binder 目标节点名（启发式：bind(rt, node, ...) 的第二参数）。'''
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
            # owner 契约（与 SurfaceSinkOwnershipRegistryTest 一致）：最近一次调用匹配的接收者；
            # 仅当接收者是泛化名(node/result/target)时，回退到向上查找的声明 owner。
            matches = list(CALL_RE.finditer(line))
            if matches:
                match = matches[-1]
                owner, owner_line = match.group(1), index + 1
                property_name = match.group(2)
            else:
                match = MREF_RE.search(line)
                if match is None:
                    continue
                owner, owner_line, property_name = match.group(1), index + 1, match.group(2)
            # 确定性 owner 契约：owner = 字面接收者，不做向上猜声明（与 Java 守护测试一致）。
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
    parser = argparse.ArgumentParser(description='surface sink 归属扫描')
    parser.add_argument('--emit', action='store_true')
    parser.add_argument('--summary', action='store_true')
    parser.add_argument('--sinks', type=int, default=0)
    parser.add_argument('--category', default=None)
    args = parser.parse_args()
    registry = scan()
    if args.emit:
        print(json.dumps({'version': 1, 'generatedAt': '2026-09-10', 'entries': registry},
                         ensure_ascii=False, indent=2))
        return 0
    counts = {}
    for entry in registry:
        counts[entry['category']] = counts.get(entry['category'], 0) + 1
    print('== surface sink 归属扫描（src/main） ==')
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