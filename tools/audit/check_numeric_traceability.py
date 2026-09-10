# -*- coding: utf-8 -*-
'''P1 数值直出反向门禁：审计包 §7 的每个数值必须能在被声明脚本的现输出中定位。

用法（仓库任意工作树，默认不阻断）：
    python tools/audit/check_numeric_traceability.py            # 核验并报告，不阻断（exit 0）
    python tools/audit/check_numeric_traceability.py --strict   # 需要把关时：不一致即 exit 1

检查三层：
  1) 反向覆盖：文档 §7 代码块每一行的每个数值，都要在 auditA_v25_full.py 现输出中按容忍度定位；
  2) 正向基准：tools/audit/numeric_baseline.json 的关键值必须与脚本现输出一致（小数容忍 0.005）；
  3) 色值基准：基准表里的 ARGB 十六进制必须能在脚本文本中定位。

踩坑（已固化进本脚本）：文档排版会把 ASCII '-' 换成 U+2212 等符号，比对前必须先做符号规范化，
否则产生假红（本门禁第一版即被 ΔL 行的 U+2212 误报）。
'''

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from repo_paths import REPO_ROOT, TOOLS_AUDIT, AUDIT_PACKAGE  # noqa: E402

FENCE = chr(96) * 3
NUM = re.compile('-?[0-9]+(?:[.][0-9]+)?')
MINUS_VARIANTS = (chr(0x2212), chr(0xFF0D), chr(0x2013), chr(0x2014), chr(0xFF0B), chr(0x2015))
FLOAT_TOLERANCE = 0.005


def normalize(text):
    '''排版符号规范化：各类减号/正号统一到 ASCII，避免假红。'''
    for symbol in MINUS_VARIANTS:
        text = text.replace(symbol, '-')
    return text


def script_output(script_path):
    proc = subprocess.run([sys.executable, str(script_path)], capture_output=True, text=True,
                          encoding='utf-8', errors='replace')
    if proc.returncode != 0:
        raise SystemExit('[FAIL] 脚本执行失败 rc=%d: %s\n%s' % (proc.returncode, script_path, proc.stderr[:400]))
    return proc.stdout


def appendix_block(doc_text):
    marker = '## 7. 附录 A'
    if marker not in doc_text:
        raise SystemExit('[FAIL] 文档缺少章节标记: %s' % marker)
    tail = doc_text.split(marker)[1]
    chunks = tail.split(FENCE)
    blocks = chunks[1::2]
    if not blocks:
        raise SystemExit('[FAIL] §7 未找到数值代码块')
    return max(blocks, key=len)


def contains(values, target, tolerance=FLOAT_TOLERANCE):
    return any(abs(value - target) <= tolerance for value in values)


def main():
    parser = argparse.ArgumentParser(description='审计包数值直出反向门禁')
    parser.add_argument('--repo', default=None, help='仓库根（默认由脚本位置推导）')
    parser.add_argument('--doc', default=None, help='审计包路径（默认 docs/开发者文档/审计包-全库默认液态玻璃样式.md）')
    parser.add_argument('--strict', action='store_true', help='不一致时 exit 1（默认仅报告）')
    args = parser.parse_args()

    root = Path(args.repo).resolve() if args.repo else REPO_ROOT
    audit_dir = root / 'tools' / 'audit'
    doc_path = Path(args.doc).resolve() if args.doc else root / 'docs' / '开发者文档' / '审计包-全库默认液态玻璃样式.md'
    for path in (audit_dir, doc_path):
        if not path.exists():
            raise SystemExit('[FAIL] 路径不存在: %s' % path)

    producer = audit_dir / 'auditA_v25_full.py'
    baseline_path = audit_dir / 'numeric_baseline.json'
    output = normalize(script_output(producer))
    script_numbers = [float(token) for token in NUM.findall(output)]

    errors = []
    doc_text = normalize(doc_path.read_text(encoding='utf-8'))
    block = appendix_block(doc_text)
    lines = [line.strip() for line in block.strip().splitlines() if line.strip()]
    checked = 0
    for index, line in enumerate(lines, 1):
        tokens = [float(token) for token in NUM.findall(line)]
        if not tokens:
            continue
        checked += 1
        missing = [token for token in tokens if not contains(script_numbers, token)]
        if missing:
            errors.append('§7 L%02d 数值未溯源 %s :: %s' % (index, missing, line[:100]))

    baseline = json.loads(baseline_path.read_text(encoding='utf-8'))
    for item in baseline.get('floats', []):
        key, expected = item['key'], float(item['value'])
        pattern = re.compile(r'^%s\s+([0-9.]+)\s*$' % re.escape(key), re.M)
        match = pattern.search(output)
        if match is None:
            errors.append('基准 key 在脚本输出中缺失: %s' % key)
            continue
        actual = float(match.group(1))
        if abs(actual - expected) > FLOAT_TOLERANCE:
            errors.append('基准不一致 %s: 基准=%s 脚本=%s' % (key, expected, actual))
    for item in baseline.get('hex', []):
        token = item['value']
        if token not in output:
            errors.append('色值基准未见于脚本输出: %s (%s)' % (item['key'], token))

    print('== 数值直出反向门禁 ==')
    print('声明脚本 : %s' % producer.relative_to(root))
    print('文档     : %s' % doc_path.relative_to(root))
    print('脚本数值 : %d 个 token | §7 含数值行: %d 行' % (len(script_numbers), checked))
    print('正向基准 : %d 浮点 + %d 色值' % (len(baseline.get('floats', [])), len(baseline.get('hex', []))))
    if errors:
        print('')
        for message in errors:
            print('[FAIL] %s' % message)
        print('')
        print('== 不一致：文档数值必须由脚本直出（手抄会在这里暴露） ==')
        return 1 if args.strict else 0
    print('')
    print('== PASS：§7 全部数值均可在脚本现输出中逐位定位，且基准一致 ==')  # 无差异
    return 0


if __name__ == '__main__':
    sys.exit(main())