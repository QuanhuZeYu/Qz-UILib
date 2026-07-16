# 字体精确分行测试依赖平台 advance

## 错误现象

首轮远端 push 将 `4.0` 从 `4c0c6b8` 推进至 `2bbb0561` 后，GitHub Actions run [`29505270305`](https://github.com/QuanhuZeYu/Qz-UILib/actions/runs/29505270305) 在 2396 项测试中仅失败一项：`TextLayoutServiceTextContentModeTest.shouldTreatSectionCodesAsVisibleCharactersInUiLibRawTrimAndWrap`。该测试在 `2bbb0561` 的 `src/test/java/club/heiqi/uilib/font/layout/TextLayoutServiceTextContentModeTest.java:64` 期望换行结果 `[A§, aB]`，Ubuntu runner 实际得到 `[A§, a, B]`。

## 触发场景

测试用 `new Font("Dialog", Font.PLAIN, 14)` 建立字体目录，却用 `getStringWidth("A§")` 形成精确 wrap 边界，并断言 `A§aB` 的具体分行。`Dialog` 是平台逻辑字体；其实际字形映射与 advance 可随 JRE、字体配置和操作系统变化。fixture 未固定 `A`、`§`、`a`、`B` 的宽度，因此本地结果不能约束 Ubuntu runner 的分行结果。

## 根本原因

生产 raw 路径没有跳过 `§` 后字符：`src/main/java/club/heiqi/uilib/font/layout/TextLayoutService.java:825-895` 的 trim/wrap 实现均逐 code point 累加 advance。宽度读取由同文件 `:668-689` 先查 runtime width 表，未命中才回落 AWT 测量。失败来自测试把平台字体测得的 advance 当成固定 fixture，而不是生产 raw 逐码点语义回归。

当 `a + B` 在某平台上大于测试以 `A + §` 得到的 wrapWidth 时，第二行会继续拆成 `a` 与 `B`；因此固定期望 `[A§, aB]` 在未固定 advance 时不具备跨平台确定性。

## 修复方案

提交 [`afc017f7`](https://github.com/QuanhuZeYu/Qz-UILib/commit/afc017f7a28735921f4c66be50e0f5c6f6738115) 将该测试改为只对目标 fixture 调用 `createService('A', '§', 'a', 'B')`，并在 `src/test/java/club/heiqi/uilib/font/layout/TextLayoutServiceTextContentModeTest.java:57,68-76` 的局部 `GlyphPageManager` runtime width 表中把四个码点的 NORMAL advance 固定为 `1.0F`。其他测试仍保留默认字体测量，生产实现未改。

该修复已由 merge commit [`a80da548`](https://github.com/QuanhuZeYu/Qz-UILib/commit/a80da54885573b8d6b719e8c7bda22f5b2b09aef) 显式合入本地 `4.0`，目标与全量本地协议测试均成功。此结果只证明本地修复基线，不等同于 Ubuntu CI 已通过。

## 预防措施

- 对 trim、wrap、ellipsis 等精确边界测试，在测试私有的 runtime width 表中固定全部参与断言的码点 advance；不得依赖 `Dialog` 等平台逻辑字体映射。
- fixture 只覆盖被测码点，避免把全局字体测量替换成假实现，从而保留其他测试对真实默认路径的覆盖。
- 断言具体分行前先确认 wrapWidth 与每个目标行的 advance 关系由 fixture 明确定义，而不是恰好由开发机字体满足。
- 本地协议测试成功后仍须按远端最终 HEAD 核验新 CI；最终 `4.0` tip 对应的新 CI 成功且包含候选与 Release 正文前，禁止创建 `4.6.0` tag。
