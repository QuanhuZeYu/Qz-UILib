# GitHub Release draft 数字 ID 丢失

## 现象

GitHub Release publish run 成功创建并上传四项资产到 draft 后，随后的 Draft 复验报告“远端缺少预期 Draft Release”。draft 实际仍存在且资产完整，但自动流程未进入正式化。

## 触发场景

写流程通过高层 tag 命令创建 draft，却丢弃 Create API 返回的稳定对象身份；后续步骤重新按 Release list/tag 发现刚创建的 draft。GitHub 的 published-by-tag 端点本来就不返回 draft，而 list 可见性也不能替代本次写事务的对象身份。

## 根因

流程把 tag 当作 draft 写事务的强身份，未保存 Create API 响应中的正整数 `id` 与绑定该 ID 的 `upload_url`。因此创建、上传、Draft 复验、正式化和最终复验不是同一条对象身份链；平台 list 返回空或可见性延迟时会错误失联。Release API 的 `created_at` 锚定 tag 时间，不能据此证明 draft 是旧对象；list 为空的平台细因保持 UNKNOWN。

## 修复

- 新增只写 REST actuator：从 Create JSON 响应验证正整数 ID、API URL、upload URL、tag/title/draft/prerelease，再只向该 upload URL 上传精确四资产。
- Draft 复验按 `GET /releases/{id}` 完整下载并核对四资产；正式化只对同一 ID PATCH `draft=false`；最终 Published 复验继续绑定同一 ID。
- `4.6.2` 现有 draft 仅允许专用 recovery mode 绑定固定 ID `357902877`。该模式从固定 immutable tag 重建本 run bundle，正式化前按 ID 完整复验，不创建、不续传、不覆盖。

## 预防

- Create 型远端写操作必须把响应对象 ID 作为后续事务身份；禁止从展示 URL 反解析、按 list 第一项或按 tag 重新发现刚创建对象。
- published-by-tag 只用于判断 published 冲突或无 ID 的既有正式 Release 幂等复验，不得用于查找 draft。
- Static/SelfTest 阻断 `gh release create/edit`、固定 sleep、动态 recovery ID、tag/list draft 再发现与 ID 链漂移；资产或状态复验失败时保留 draft 并停止，禁止自动清理、覆盖或重试。
