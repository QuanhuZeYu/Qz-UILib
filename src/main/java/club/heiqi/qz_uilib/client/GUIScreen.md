# GuiScreen 类字段与方法列表
## 字段列表

1. protected static RenderItem itemRender​​ 用于绘制成就图标的渲染器（基于 ItemStack）

2. public Minecraft mc​​ Minecraft 主类实例的引用

3. public int width​​ 屏幕宽度

4. public int height​​ 屏幕高度

5. protected List<GuiButton> buttonList​​ 存储所有按钮的列表

6. protected List<GuiLabel> labelList​​ 存储所有标签的列表

7. public boolean allowUserInput​​ 是否允许用户输入

8. protected FontRenderer fontRendererObj​​ 字体渲染器实例

9. private GuiButton selectedButton​​ 当前被按下的按钮

10. private int eventButton​​ 记录鼠标事件按钮

11. private long lastMouseEvent​​ 上次鼠标事件的时间戳

12. private int field_146298_h​​ 触摸屏事件计数器（未明确用途）

13. private static final String __OBFID​​ 混淆标识符（编译时使用）

## 方法列表​​
1. public void drawScreen(int mouseX, int mouseY, float partialTicks)​​ 核心渲染方法：绘制所有按钮和标签

2. protected void keyTyped(char typedChar, int keyCode)​​ 键盘输入处理（按 ESC 关闭界面）

3. public static String getClipboardString()​​ 从系统剪贴板获取字符串

4. public static void setClipboardString(String copyText)​​ 向剪贴板写入字符串

5. protected void renderToolTip(ItemStack itemIn, int x, int y)​​ 渲染物品悬浮提示（Tooltip）

6. protected void drawCreativeTabHoveringText(...)​​ 绘制创造模式标签的悬浮文本

7. protected void func_146283_a(...)​​ 绘制多行悬浮文本（内部调用 drawHoveringText）

8. protected void drawHoveringText(...)​​ 实现悬浮文本的渲染逻辑（带背景框）

9. protected void mouseClicked(int mouseX, int mouseY, int mouseButton)​​ 鼠标点击事件处理（触发按钮动作）

10. protected void mouseMovedOrUp(...)​​ 鼠标移动/释放事件处理

11. protected void mouseClickMove(...)​​ 鼠标拖拽事件处理（默认空实现）

12. protected void actionPerformed(GuiButton button)​​ 按钮点击回调（需子类实现）

13. public void setWorldAndResolution(Minecraft mc, int width, int height)​​ 初始化屏幕尺寸和资源（触发 initGui）

14. public void initGui()​​ 初始化 GUI 元素（按钮/标签等）

15. public void handleInput()​​ 输入事件分发（调用鼠标/键盘处理）

16. public void handleMouseInput()​​ 处理鼠标事件（点击/移动/释放）

17. public void handleKeyboardInput()​​ 处理键盘事件（调用 keyTyped）

18. public void updateScreen()​​ 每帧更新逻辑（默认空实现）

19. public void onGuiClosed()​​ 界面关闭时回调（禁用键盘重复事件）

20. public void drawDefaultBackground()​​ 绘制默认背景（调用 drawWorldBackground）

21. public void drawWorldBackground(int tint)​​ 根据世界状态绘制背景（渐变或纹理）

22. public void drawBackground(int tint)​​ 绘制纹理背景（使用 optionsBackground）

23. public boolean doesGuiPauseGame()​​ 返回界面是否暂停游戏（默认 true）

24. public void confirmClicked(boolean result, int id)​​ 确认对话框回调（默认空实现）

25. public static boolean isCtrlKeyDown()​​ 检查 Ctrl 键（或 Mac Meta 键）是否按下

26. public static boolean isShiftKeyDown()​​ 检查 Shift 键是否按下

## 关键字段与方法总结​​
### 核心字段​​
* buttonList 和 labelList​​ 管理界面中所有交互元素（按钮/标签）的核心容器。

* mc(Minecraft 实例)​​ 提供访问游戏状态、资源管理器等关键功能。

* itemRender(RenderItem)​​ 负责渲染物品图标，用于 Tooltip 和 GUI 物品展示。

* fontRendererObj​​ 所有文本渲染的基础工具。

### 核心方法​​
* drawScreen(...)​​

    核心渲染入口​​：遍历 buttonList和 labelList绘制所有元素。子类通常覆盖此方法扩展 UI。

* initGui()​​

    初始化入口​​：子类在此方法中添加按钮 (buttonList.add(...)) 和布局 UI。

* actionPerformed(GuiButton)​​

    事件处理核心​​：子类实现按钮点击逻辑（如打开新界面、执行操作）。

* drawHoveringText(...)​​

    悬浮提示实现​​：绘制带背景框的多行文本（被物品 Tooltip 和创造标签调用）。

* handleInput()与输入处理​​

    统一管理鼠标 (handleMouseInput) 和键盘 (handleKeyboardInput) 事件的分发。

* setWorldAndResolution(...)​​

  初始化触发点​​：设置界面尺寸并调用 initGui，是界面生命周期的起点。

## 功能亮点​​
* 剪贴板支持​​：getClipboardString/setClipboardString实现系统剪贴板交互。

* 跨平台键位检测​​：isCtrlKeyDown兼容 Windows/Mac 的 Ctrl/Meta 键。

* 事件分发​​：通过 MinecraftForge.EVENT_BUS发送按钮事件（如 ActionPerformedEvent）。

* 自适应背景​​：drawWorldBackground根据是否存在世界切换背景渲染模式。