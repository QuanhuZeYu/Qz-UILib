# 规格文档

本目录存放 scene 栈现行的局部规范：API 规范、宿主语义、物品渲染合同与视觉规格。它固化局部页面或专项能力的预期行为，不等同于通用接入手册；对外使用方式以 [使用文档](../../使用文档/README.md) 为准。

每份材料的状态由其**文件头部状态行**唯一声明：**现行规范**是实现与验收依据；**在办**材料尚未实施、不构成现行要求。本索引按该状态分节，不另行判定效力；发现文件头部与本节不一致时，以文件头部为准并就地修正本节。文件内部标注为施工记录的章节不改变其整体身份。设计意图与缺陷根因以最近的代码注释为准。

已收口或已过期的专项材料不留在本目录，统一归档到 [历史报告/](../../历史报告/README.md)；引用时直接引其归档路径，只作「当时做过什么、验证到什么程度」的证据。

## 现行规范

- [scene基础API规范.md](scene基础API规范.md)：Scene Primitive API 规范（primitive/wrapper 分层、Props/Result 形态、语义化不变量清单）。
- [UI投影宿主语义.md](UI投影宿主语义.md)：统一 content/projection/host/input 与 state/intent 高层语义（scene 输入层语义母本）；Input Scope 与 State/Intent 两节现行且不随实现删除，U0 实现已删除、复活条件见其「实施」节。
- [物品视觉渲染接缝.md](物品视觉渲染接缝.md)：Breaking major 的 snapshot-only ItemStack icon 合同（完整原版委托 + RenderSemantics + 分级）；迁移期版本事实不在此固化，指向实时真源。
- [数据表可编辑单元格视觉规格.md](数据表可编辑单元格视觉规格.md)：DataTable 可编辑列的现行视觉意图（well/inset 输入槽、三态可辨、列级输入列带）；色值表与「给 fixer」章节是立项期记录，实现以主题 token 为准。
- [材质配置动效.md](材质配置动效.md)：Material 3 配置页与最小 Motion 产品规格。
- [网络层方案.md](网络层方案.md)：网络层现行方案（内容语义 Channel + Fetch + Stream + Store、Vanilla mixin 适配器、Forge 兼容适配器）；Realtime 子层仍实验性。
- [网络编解码线格式.md](网络编解码线格式.md)：网络层内容 envelope、可选 POJO codec 与分片格式。
- [网络原版Mixin注入策略.md](网络原版Mixin注入策略.md)：vanilla custom payload early mixin 注入点与传输策略。
- [投放职责聚合方案.md](投放职责聚合方案.md)：scene 投放层的现行判据与立法（职责边界判据、flush 收口立法、装配唯一、投影化触发条件）；立项前提、聚合清单、施工批次与实测记录已归档。

## 在办

- [文本延迟批处理重接线方案.md](文本延迟批处理重接线方案.md)：延迟文本批处理接回 scene 栈的设计（G1，用户裁定保留 + 重新接线，尚未实施）。
