# 决策：滚动API全链路改进（P0-P4，C1-C6）

## 日期
2026-07-01

## 背景
SceneScrollContainer.attach 要求作者手传 contentChangedSignal（layout完成通知），是所有主流声明式响应式框架中唯一要作者管layout通知的。4控件Props用 scrollbarContentSignal(ReadableSignal) 表达"要不要滚动条"，语义不诚实。forEach必须传keyFn，简单列表场景冗余。无一行建带滚动条列表的便捷方法。

## 决策
1. **layoutDoneSignal 下移 runtime**：SceneRuntime 新增 layoutDoneSignal + __bridgeLayoutEpoch，host 委托 runtime。epoch仍归引擎（纯int），signal归runtime，host桥接。守I6（signal归数据层原语）。
2. **SceneScrollbar.Props 删 contentChangedSignal**：create内改读 rt.layoutDoneSignal()。作者不再手传layout通知。
3. **SceneScrollContainer attach 去 contentChangedSignal 参数**：ScrollbarSpec同步删。P0外泄消除闭环。
4. **4控件 scrollbarContentSignal → showScrollbar boolean**：语义诚实，作者只声明要不要滚动条。
5. **forEach 无keyFn重载**：Function.identity()做默认key，对齐SolidJS <For>隐式key。值语义/重复引用场景须用带keyFn重载。
6. **scrollList 一体化方法**：一行建container+viewport+scrollbar+forEach，对齐Slint ListView零配置。

## 取代
- 取代 DECISION-20260630-scrollbar-layout-done-signal.md 的"host持signal"方案（signal下移runtime）
- 取代 DECISION-20260701-scene-scroll-container-attach-facade.md 的"attach必参contentChangedSignal"描述

## 理由
- 对标Compose/SolidJS/Slint，作者层从7概念+手传layout通知降到5概念+零layout通知
- scrollbar订阅runtime signal比作者手传更可靠（杜绝忘传导致thumb不更新）
- showScrollbar boolean比signal!=null更诚实
- 无keyFn重载+scrollList降低简单场景入门门槛

## 影响
- 破坏性API变更（Props删字段+attach签名变+scrollbarContentSignal重命名），全仓同步迁移
- 性能：双重去重（host epoch比对+scheduler值比对）保证不劣化
- overlay独立引擎不bump runtime signal，当前overlay无scrollbar不触发，记为已知边界

## 出处
- commits: b197bc7a→f98f8480（6 commit串行）
- Oracle复审: 通过，守I6/I1-I12/R1-R12
