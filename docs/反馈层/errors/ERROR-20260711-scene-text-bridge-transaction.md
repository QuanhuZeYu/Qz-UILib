# Scene 文本桥注册事务不完整

## 错误现象

lwjgl3ify 文本监听注册或文本输入启动失败后，监听器可能残留；注销时任一步骤抛错还会跳过后续清理。

## 触发场景

可选模组类或方法不完整，或者 add/begin/end/remove 的反射调用在已产生外部副作用后抛出异常。

## 根本原因

旧实现先执行 add 再解析 remove，且只在全部步骤成功后发布注册状态。失败路径丢失半完成事务信息；
注销又把 end 与 remove 放在同一 try 中，前一步失败会阻断后一步。

## 修复方案

注册前构造不可变完整计划，weak add/remove 不完整时整体回退 strong 配对；add 与 begin 前分别发布 pending。
统一失败出口执行回滚，end/remove 独立尝试并逐项清 pending，未完成步骤保留到后续 unregister 重试。
类探测与注册统一使用文本桥锚 classloader，并以 `initialize=false` 加载。

## 预防措施

第三方反射桥必须在任何外部副作用前验证正反操作完整成对；半副作用异常须有故障注入测试。
可选依赖探测不得触发静态初始化，也不得吞掉 `VMError` 或 `ThreadDeath`。
