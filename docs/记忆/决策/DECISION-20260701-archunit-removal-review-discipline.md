# 决策：移除 ArchUnit 自动守卫，改评审纪律

## 日期
2026-07-01

## 背景
scene 测试体系曾用 ArchUnit（archunit-junit4:1.3.0）守卫 L2 纯数学边界（规则3：layout包禁依赖 runtime/input/reactive/paint）。规则1/2 待 B5 启用。

## 决策
移除 ArchUnit 依赖与 ArchitectureTest.java，L2 纯数学边界改由：
1. 评审纪律（测试体系约定.md §6 架构纪律 checklist）
2. package-info.java 声明（layout 包 import 禁区）
3. AGENTS.md 测试规范索引（评审者必读）

## 理由
- ArchUnit 规则维护成本（jar 依赖 + 规则同步）超过收益
- scene 包结构稳定、L2 import 禁区是低认知成本机械核对
- 28 处裸 InputFrameBuilder 残留经 Oracle 裁决全合法（§7.1 五类白盒场景），规则1无法无豁免启用

## 影响
- L2 边界纯软约束，依赖评审者知晓规则（已由 AGENTS.md 索引缓解）
- 后续可选补零依赖 grep 脚本断言 `scene/layout/**` 无四包 import

## 出处
- commit: 7b20c36c（移除）+ 539c0843（文档收尾）
- Oracle 终审：ses_0e2befc69ffeQBNAg7yh6rdluQ
