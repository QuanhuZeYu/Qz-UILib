# GitHub Release 与外部渠道耦合

## 现象

4.6.2 immutable tag 已固定，但旧自动发布链因第三方渠道状态、控制面 checkout、nested 权限图和 draft ID 处理问题多次失败，仓库自有 Release 被扩大故障域阻断。

## 触发场景

通用 tag workflow 同时承担多个渠道、可复用控制面、创建上传与正式化；只读模式仍经过含写权限的调用图，并依赖旧 tag 中不存在的新 checker。

## 根因

把多个分发渠道误建模为单一事务，也把执行合同程序与 immutable tag 数据绑定到同一 checkout；通用自动化的状态空间和权限面超过当前一次性恢复需求。

## 修复

旧通用 workflow、publication/advisory gate 与外部 checker 已退役。仓库现只保留固定 4.6.2 recovery：`verify` 只读重建并核对 draft，条件 `publish` job 拥有唯一写权限且只 PATCH ID `357902877` 的 `draft=false`。

## 预防

固定 recovery 不扩展为通用入口。未来通用 tag、JitPack、Maven 或其他渠道必须各自独立任务定义权限、身份、制品、重试与状态真值；任何渠道结果不得冒充另一渠道。tag 永不可移动，远端异常保留现场交用户决定。
