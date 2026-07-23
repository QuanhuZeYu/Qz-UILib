# 决策：JitPack publication

## 历史结论

4.6.0 暴露了普通本地 publication 正确却无法证明 JitPack canonical classifier 正确的问题。4.6.1 曾采用 canonical GAV 映射、仅 JitPack 模式禁用 GMM、远端 POM/main/dev/sources/hash 与 clean `:dev` consumer 闭环，并已获得真实成功结果；不可移动 tag 策略保持。

## 当前选择

仓库不再维护 JitPack advisory、publication checker 或 branch gate。该清理不否定历史 4.6.1 结果，也不宣称当前存在通用 JitPack 发布能力。

固定 `4.6.2` recovery 与 JitPack 零依赖，不读取、触发或写入 JitPack；第三方渠道状态不得阻断或冒充 GitHub Release 状态。

## 未来重建条件

若恢复 JitPack，必须另开任务重新定义：

- canonical GAV、POM/classifier、GMM 策略与不可移动 tag；
- main/dev/sources 的远端 hash、Build API/build log 和 clean consumer；
- 独立 workflow、权限、重试、pending/确定性错误分类与状态写回；
- 与 GitHub Release、Maven 的零依赖边界。

历史脚本和 workflow 不得作为现行入口恢复使用。
