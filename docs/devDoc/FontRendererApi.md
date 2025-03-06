# FontRenderer公共函数

```java
/**
 * 字体渲染器初始化类，负责加载字体资源、预计算颜色代码和字形尺寸。
 *
 * @param gameSettings   游戏设置对象，包含渲染相关配置（如防色盲模式）
 * @param fontLocation   字体纹理资源位置
 * @param textureManager 纹理管理器实例，用于加载和管理游戏资源
 * @param useUnicode     是否启用Unicode字符集支持
 */
public FontRenderer(GameSettings gameSettings, ResourceLocation fontLocation, TextureManager textureManager, boolean useUnicode) {}

/**
 * 当重载资源包时触发
 */
public void onResourceManagerReload(IResourceManager rm) {}

/**
 * 在指定坐标处绘制带阴影的字符串
 */
public int drawStringWithShadow(String text, int x, int y, int color) {}

/**
 * 在指定坐标处绘制不带阴影的字符串
 */
public int drawString(String text, int x, int y, int color) {}

/**
 * 在指定坐标处绘制带阴影或不带阴影的字符串。
 * <p>
 * 此方法首先启用 alpha 通道透明处理，重置文本样式后：
 * - 若需要阴影，先在偏移位置（右下角）渲染阴影层
 * - 再在原始位置渲染主体文本
 * - 最终返回两者的最大宽度以确保完整覆盖区域
 *
 * @param text       要渲染的字符串内容
 * @param x          字符串左下角 X 坐标
 * @param y          字符串左下角 Y 坐标
 * @param color      文本颜色值（RGB 或 ARGB 格式）
 * @param dropShadow 是否启用阴影效果
 * @return 绘制完成的字符串区域的最大宽度
 * @see #enableAlpha()
 * @see #resetStyles()
 * @see #renderString(String, int, int, int, boolean)
 */
public int drawString(String text, int x, int y, int color, boolean dropShadow) {}

/**
 * 计算字符串在当前字体设置下的总显示宽度。
 * <p>
 * 此方法会遍历字符串中的每个字符，累加其显示宽度。特别处理控制字符（如字体格式控制符），
 * 并根据控制字符类型调整后续字符的宽度计算规则。
 *
 * @param inputText 要计算宽度的目标字符串，可能为 {@code null}
 * @return 字符串的总显示宽度（单位：像素），若输入为 {@code null} 则返回 0
 * @see #getCharWidth(char)
 */
public int getStringWidth(String inputText) {}

/**
 * 获取指定字符在当前渲染上下文中的实际显示宽度。
 *
 * @param character 要查询宽度的字符
 * @return 字符的显示宽度，特殊控制字符返回-1，空格返回4，其余字符根据字体定义返回实际像素宽度
 */
public int getCharWidth(char character) {}

/**
 * 将输入字符串按从左到右或从右到左的顺序遍历，截取宽度不过 maxWidth 的子字符串。超宽部分会被截断
 */
public String trimStringToWidth(String inputText, int maxWidth) {}

/**
 * 将输入字符串按从左到右或从右到左的顺序遍历，截取宽度不超过 maxWidth 的子字符串。超宽部分会被截断，最终结果可能根据 reverseOrder 参数决定是否反转输出
 */
public String trimStringToWidth(String inputText, int maxWidth, boolean reverseOrder) {}

public void drawSplitString(String str, int x, int y, int wrapWidth, int textColor) {}

/**
 * 计算换行后的总行数并乘以字体高度得到总尺寸
 */
public int splitStringWidth(String inputText, int maxLineWidth) {}

public void setUnicodeFlag() {}

public boolean getUnicodeFlag() {}

/**
 * 
 */
public void setBidiFlag(boolean p_78275_1_) {}

/**
 * 将格式化后的多行文本按换行符分割为列表
 */
public List<String> listFormattedStringToWidth(String inputText, int wrapWidth) {}
```