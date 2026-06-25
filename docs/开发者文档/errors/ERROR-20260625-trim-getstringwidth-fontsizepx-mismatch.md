# ERROR-20260625 trimRaw 与 getStringWidth 的 fontSizePx 口径不一致

## 错误现象

`TextLayoutServiceTextContentModeTest.shouldTreatSectionCodesAsVisibleCharactersInUiLibRawTrimAndWrap`
在 4.0 主分支上一直失败：
- `trimStringToWidth("A§aB", getStringWidth("A§", UILIB_RAW), UILIB_RAW)` 期望 `"A§"`，实际返回空串

## 触发场景

UILIB_RAW 模式下调用 `trimStringToWidth(text, targetWidth, mode)`，且 targetWidth 来自 `getStringWidth(text, mode)` 时。

## 根本原因

两条路径的字符宽度口径 2 倍不一致：
- `getStringWidth(text, UILIB_RAW)` → `getSegmentWidth` → `getCodepointWidth(cp, style)`（无 fontSizePx）= defaultWidth（9px 空间）
- `trimRawStringToWidth` 的无 fontSizePx 重载 `:783` 传 `DEFAULT_FONT_SIZE_PX=18` → `getCodepointWidth(cp, style, 18)` = defaultWidth × 18/9 = 2×defaultWidth（18px 空间）

targetWidth 来自 getStringWidth（9px 空间），trim 每字符按 18px 空间算，A 单字符（2×宽）就超出 targetWidth（1×宽累加），trim 立即 break 返回空。

`wrapRawStringToWidth :841` 本就用无 fontSizePx 版本（9px），唯独 trim 的无参重载错用 18。

## 修复方案

`TextLayoutService.java:783` — trimRawStringToWidth 无 fontSizePx 重载的缩放基准从 `TextMeasureStyle.DEFAULT_FONT_SIZE_PX(18)` 改为 `(int) FontConfig.charSize(9)`，缩放因子=1，与 getStringWidth、wrapRawStringToWidth 口径统一。

- `:274` 路径（MC 兼容 trimStringToWidth，targetWidth 来自 getStringWidth 9px 空间）现在 trim 也走 9px，一致
- `:319` 路径（UI Lib trimStringToWidth(text, targetWidth, style)，传 resolvedStyle.getFontSizePx()）不受影响

## 预防措施

- 同一公共 API 的不同重载必须保持宽度口径一致；无 fontSizePx 重载不应默认用更大的 UI 像素字号
- trim/wrap/getStringWidth 三者共用同一字符宽度来源时，缩放基准必须统一
- 测试失败若长期存在，必须排查而非忽视，往往是口径不一致的信号

## 依据

- 修复 commit：`007ac778`（分支 `fix/glyph-coordinate-system-mismatch`）
- 测试验证：45 个字体测试全绿（含此前一直失败的用例转绿）
- 改前验证：git stash 跑改前测试确认该用例在 4.0 主分支上本就失败（与字号统一改动无关）
