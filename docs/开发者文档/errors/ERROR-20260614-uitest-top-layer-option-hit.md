# ERROR-20260614-uitest-top-layer-option-hit

## 错误现象

- `/qzuilib test` 全量断言运行到 `VIS-CTRL-005` 时，select top-layer 弹层已打开、目标 option 也存在，但自动断言记录 `selectOptionHit=false`。
- 失败摘要显示当前值仍为「石头」，日志仍停留在「等待 select change」，说明自动断言没有真正命中「红石」选项。

## 触发场景

- 在 `UiTestDocumentPageController.runAllAssertions()` 中连续切换多个分组与样例后运行 Controls 组 `VIS-CTRL-005`。
- `UiTestControlsAssertionRunner.assertSelectTable` 直接取 `targetOption.getDocumentBounds()` 的中心点并发送鼠标事件。

## 根本原因

- select 弹层注册为 document top-layer 后，其真实命中链路以 top-layer 布局和 hit-test 结果为准。
- 直接使用 option 自身的文档边界中心点并不总能落到真实 top-layer option 命中区域，连续重建页面与弹层布局后更容易出现边界偏移。
- 测试辅助 `clickOptionByMouseHit` 已使用扫描 listbox 的真实 hit-test 命中方式，但自动断言实现没有复用同类策略。

## 修复方案

- 将 `UiTestControlsAssertionRunner` 中 `VIS-CTRL-005` 的 option 点击改为扫描 top-layer listbox 的真实 `findElementAt` 命中。
- 从命中节点向上查找同一 listbox 内的 `option`，确认文本为「红石」后再发送鼠标 down/up。
- 保留原有 `select.getAttribute("value")` 与 change 日志断言，确保命中后确实更新 select 与 table。

## 预防措施

- 涉及 top-layer、弹层、变换后命中、滚动后命中的自动断言，不要直接信任目标元素静态边界中心点。
- 优先通过 `HtmlLikeDocumentWidget.findElementAt` 执行真实 hit-test，再校验命中节点或其祖先是否属于目标元素。
- 若测试辅助中已有更稳的命中策略，自动断言实现应同步采用，避免单测手动路径通过但全量自动路径失败。
