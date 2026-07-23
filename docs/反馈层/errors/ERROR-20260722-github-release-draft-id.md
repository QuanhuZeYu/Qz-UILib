# GitHub Release draft 数字 ID 丢失

## 现象

4.6.2 publish run 创建并上传四项资产后，后续步骤把 Release ID 截断/丢失，未进入正式化；当时 draft `357902877` 仍存在且资产完整。

## 触发场景

写流程没有把 Create 响应中的稳定数字 ID 贯穿整个事务，后续又依赖 list/tag 发现或错误的单元素标量处理。

## 根因

tag 不能替代 draft 写事务对象身份；published-by-tag 也不会返回 draft。未冻结完整数字 ID 时，创建、上传、复验与 PATCH 不是同一对象链。

## 修复

通用 Create/upload 自动化已删除。固定 recovery 只硬编码既有 ID `357902877`：从固定 tag 重建同 run bundle，PATCH 前按 ID 核对 notes、状态及恰好四个资产；唯一写操作是同 ID `draft=false`；最终同时按 ID/tag 复验。该固定 ID 链已于 2026-07-23 成功正式化 Release `4.6.2`。

## 预防

固定 recovery 禁止动态 ID、list 首项、URL 反解析、Create、上传、覆盖或删除。未来若重建通用发布，必须以 Create 响应 ID 贯穿整个事务并独立建立正反测试。
