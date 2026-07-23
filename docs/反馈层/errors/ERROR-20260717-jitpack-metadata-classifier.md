# JitPack metadata 与 classifier 语义错位

## 错误现象

Qz-UILib 4.6.0 的本地 publication 与 GitHub Release JAR 正确，但 JitPack canonical 坐标下的 metadata 未正确关联分类制品，显式 `:dev` 消费返回 404。

## 触发场景

验证只停留在普通本地 publication 或 GitHub Release 资产，没有在 JitPack 改写 GAV 后端到端核对 POM、classifier、metadata 与 clean consumer。

## 根本原因

普通本地 publication 正确不代表第三方 canonicalization 后仍正确；局部结果被错误外推为远端渠道成功。

## 修复方案

4.6.1 曾通过 canonical GAV 映射、条件禁用 GMM 和远端 consumer 闭环修复，且 tag 保持不可移动。相关通用 checker、branch gate 与 advisory 后续已随极简 4.6.2 recovery 清理，不再是现行入口。

## 预防措施

任何未来 JitPack/Maven 能力必须在独立任务中重建 workflow、凭据边界、POM/classifier/hash/metadata 与 clean consumer 验收；不得把本地构建、其他渠道或历史 run 写成当前远端成功，也不得接入固定 4.6.2 recovery。
