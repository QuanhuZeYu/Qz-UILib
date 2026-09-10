# tools/audit — 审计/诊断工具链

**定位（2026-09-10 减负决定）**：本目录是**按需使用的诊断工具**，不参与提交阻断、不维护清单文件。
背景：这些工具原本服务于外部证据链（外审包）。外审取消后，其消费者只剩开发自己，
于是从「每次开发都要交税」的阻断链上摘下来，只保留「需要时能算一遍」的能力。

## 工具

| 脚本 | 作用 | 何时用 |
| --- | --- | --- |
| `auditA_v25_full.py` | WCAG 对比度/合成色数值直出（附录 A 的唯一来源） | 改主题色、写可访问性结论时 |
| `check_numeric_traceability.py` | 核验文档里的数值是否都能在脚本输出中定位（默认**仅报告**，`--strict` 才阻断） | 改完审计/验收文档数值后 |
| `surface_sink_ownership.py` | 扫描 `src/main` 的 `setBackgroundColor` / `setBorderColor` 写入者并给启发式判类 | 想确认「谁在写表面属性」时 |
| `audit_zero_proof.py` | legacy 兼容入口的零点核验（L1 调用 / L2 全引用 / L3 语义归属） | 确认兼容入口没被生产调用 |
| `repo_paths.py` / `__init__.py` | 路径推导（脚本可从任意工作树运行） | — |

## 纪律（最小）

1. 文档里的数值**不要手抄**：改数值先改脚本，再重跑 `check_numeric_traceability.py`。
2. `surface_sink_ownership.py` 的判类是启发式，`review-required` 只是「建议瞄一眼」，不是错误。
3. 没有清单文件需要维护；扫描结果随代码实时生成。

## 已退役

- `SurfaceSinkOwnershipRegistryTest` + `surface_sink_registry.json`（注册制守护，60 条目 / 18KB）：
  退役原因 = 维护税与实际收益不匹配（近 60 个提交仅 4 个触碰 sink，其中多数还是合法新增；
  39/60 条目属永远合法的 `light-slot`），且外审取消后其「对外证明」价值归零。
  真正的架构不变量（node 级单写者）改用**运行期探针**守护，见 `SceneSurfaceBinderTest` 的双写检查。