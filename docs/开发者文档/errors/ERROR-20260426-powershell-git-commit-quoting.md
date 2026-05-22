# PowerShell git commit 消息转义错误

## 错误现象

- 在 PowerShell 中执行 `git commit -m ... -m ...` 时，提交正文内嵌的 Gradle 验证命令包含未正确转义的引号，导致 `--no-configuration-cache` 被拆成 `git commit` 的参数。
- Git 报错：`error: unknown option 'no-configuration-cache'`，提交未创建。

## 触发场景

- 通过一条 PowerShell 命令同时执行 `git diff --cached --stat`、`git commit` 和 `git status`。
- `git commit` 正文中直接嵌入包含双引号和 `$env:...` 的验证命令文本。

## 根本原因

- PowerShell 中 `\"` 不是可靠的双引号转义方式，提交正文提前结束后，后续文本被传给了 `git commit` 参数解析器。
- 提交消息正文过度复杂，把可执行验证命令原文塞进命令行参数，增加了 shell 转义风险。

## 修复方案

- 不 amend，不改写历史；保留暂存区后使用更简单的提交正文重新创建提交。
- 提交正文记录验证结论即可，避免在 `git commit -m` 参数里嵌套复杂命令文本。

## 预防措施

- PowerShell 下提交正文应避免包含未转义的双引号、`$env:` 或复杂命令片段。
- 如确需多行复杂正文，优先使用简单摘要，或先准备消息文件再用 `git commit -F`，不要把复杂命令原样塞进 `-m`。
