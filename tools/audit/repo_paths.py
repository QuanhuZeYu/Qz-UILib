# -*- coding: utf-8 -*-
'''仓库路径与工具定位：脚本可从仓库任意工作树运行，不写死绝对路径。'''

from pathlib import Path

# tools/audit/repo_paths.py -> 仓库根 = parents[2]
REPO_ROOT = Path(__file__).resolve().parents[2]
TOOLS_AUDIT = REPO_ROOT / 'tools' / 'audit'
DOCS_DIR = REPO_ROOT / 'docs' / '开发者文档'
MAIN_JAVA = REPO_ROOT / 'src' / 'main' / 'java'
TEST_RESOURCES = REPO_ROOT / 'src' / 'test' / 'resources'

AUDIT_PACKAGE = DOCS_DIR / '审计包-全库默认液态玻璃样式.md'


def repo_file(*parts):
    '''按仓库根拼接路径。'''
    path = REPO_ROOT
    for part in parts:
        path = path / part
    return path