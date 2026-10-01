package club.heiqi.uilib.font.page;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import club.heiqi.uilib.font.glyph.MathGlyphKey;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.FontRuntimeMetrics;
import club.heiqi.uilib.font.FontRuntimeSettings;

/**
 * 字体运行时按码点直索引表。
 *
 * <p>本结构在 generation write barrier 内原地清理并转移给下一 runtimeVersion；宽度、字体匹配、
 * 字形状态和页槽位定位都按 {@code codepoint + FontType} 拆成 primitive array，避免热路径创建键对象，
 * 也避免换代时并存两份完整 Unicode tables。</p>
 */
public final class GlyphRuntimeTables {

    public static final int CODEPOINT_COUNT = Character.MAX_CODE_POINT + 1;
    public static final int FONT_INDEX_UNRESOLVED = -1;
    public static final int FONT_INDEX_NONE = -2;
    public static final int LOCATION_NOT_READY = -1;
    public static final int LOCATION_NO_BITMAP = -2;

    public static final byte STATE_ABSENT = 0;
    public static final byte STATE_QUEUED = 1;
    public static final byte STATE_RASTERIZING = 2;
    public static final byte STATE_UPLOAD_QUEUED = 3;
    public static final byte STATE_UPLOADING = 4;
    public static final byte STATE_RESIDENT = 5;
    public static final byte STATE_NO_BITMAP = 6;
    public static final byte STATE_FAILED = 7;
    public static final byte STATE_CANCELLED_STALE = 8;

    public static final byte GLYPH_FLAG_COLORED = 1;
    public static final byte GLYPH_FLAG_HAS_BITMAP = 2;
    /** Mathematical tiles draw their core exactly; sampling padding must not expand the quad. */
    public static final byte GLYPH_FLAG_MATH_CORE = 4;

    /**
     * 字形几何就绪代：每写入一组字形 ink/槽位几何 +1。
     *
     * <p>LaTeX 布局对定界符等基元字形依赖 ink 表（bearingY/inkHeight）做数学轴锚定；
     * 字形异步生成完成前 ink 表为 0、布局走回退值，若 LatexCache 永久缓存该回退盒，
     * 真机首帧「花括号对齐第一行」类错位不随字形就绪自愈。本代进缓存键：字形就绪
     * 即失效重布局。</p>
     */
    private volatile int inkEpoch;

    /** 宽度近似债务位图字数：{@link #CODEPOINT_COUNT} 位 / 64。 */
    private static final int WIDTH_DEBT_WORD_COUNT = (CODEPOINT_COUNT + 63) >>> 6;

    /**
     * 宽度近似债务位图：置位表示该码点曾落到「按空格宽近似」分支、真值尚未取得。
     *
     * <p>按码点对账而非计数近似次数——若只记「近似次数 / 真值写入次数」两个计数器，
     * 同一窗口内无关码点的真值写入会立刻把债务冲平，导致过早收敛。</p>
     */
    private final AtomicLongArray widthApproximationDebtNormal =
            new AtomicLongArray(WIDTH_DEBT_WORD_COUNT);
    private final AtomicLongArray widthApproximationDebtBold =
            new AtomicLongArray(WIDTH_DEBT_WORD_COUNT);
    private final AtomicInteger widthApproximationDebtCount = new AtomicInteger();
    private final AtomicInteger widthConvergeEpoch = new AtomicInteger();

    // Sparse mathematical identities share the ordinary page arrays and owner lock.
    final Map<MathGlyphKey, MathGlyphRecord> mathGlyphs = new HashMap<MathGlyphKey, MathGlyphRecord>();

    /** @return 当前字形几何就绪代（字形 ink 数据写入次数累计）。 */
    public int getInkEpoch() {
        return inkEpoch;
    }

    /** 字形几何写入完成时递增就绪代（使依赖 ink 表数据的布局缓存失效）。 */
    public void bumpInkEpoch() {
        inkEpoch++;
    }

    /**
     * 当前宽度收敛代：宽度近似债务由 &gt; 0 归零时 +1。
     *
     * <p>页面层把本代与字体换代代复合进文本测量纪元，使「近似态布局产物」在真值
     * 回填后自动失效重算，而不必等用户关掉再打开页面。收敛是幂等的：债务归零后
     * 不再产生新近似，纪元停止变化，稳态零重排。</p>
     *
     * @return 宽度收敛代（本会话内单调递增，不随 generation 重置）
     */
    public int getWidthConvergeEpoch() {
        return widthConvergeEpoch.get();
    }

    /** @return 当前未清偿的宽度近似债务码点数（诊断与测试用）。 */
    public int getWidthApproximationDebtCount() {
        return widthApproximationDebtCount.get();
    }

    /**
     * 登记一次宽度近似：该码点按空格宽排版、真值未取到。
     *
     * <p>同一码点重复登记只计一次。装配线程与布局线程都会写，故用 CAS 保证原子。</p>
     *
     * @param fontType  字重
     * @param codepoint 码点
     */
    public void markWidthApproximated(FontType fontType, int codepoint) {
        AtomicLongArray debt = widthApproximationDebtArray(fontType);
        if (debt == null || !isValidCodepoint(codepoint)) {
            return;
        }
        int index = codepoint >>> 6;
        long bit = 1L << (codepoint & 63);
        while (true) {
            long current = debt.get(index);
            if ((current & bit) != 0L) {
                return;
            }
            if (debt.compareAndSet(index, current, current | bit)) {
                widthApproximationDebtCount.incrementAndGet();
                return;
            }
        }
    }

    /**
     * 清偿一次宽度近似债务：该码点已取到真值（AWT 测量或装配回填）。
     *
     * <p>债务计数由 &gt; 0 归零时递增宽度收敛代。</p>
     *
     * @param fontType  字重
     * @param codepoint 码点
     */
    public void clearWidthApproximated(FontType fontType, int codepoint) {
        AtomicLongArray debt = widthApproximationDebtArray(fontType);
        if (debt == null || !isValidCodepoint(codepoint)) {
            return;
        }
        int index = codepoint >>> 6;
        long bit = 1L << (codepoint & 63);
        while (true) {
            long current = debt.get(index);
            if ((current & bit) == 0L) {
                return;
            }
            if (debt.compareAndSet(index, current, current & ~bit)) {
                if (widthApproximationDebtCount.decrementAndGet() == 0) {
                    widthConvergeEpoch.incrementAndGet();
                }
                return;
            }
        }
    }

    /**
     * 清空宽度近似债务（generation 生命周期重置）。
     *
     * <p>不递增收敛代：换代本身已由字体换代代区分，收敛代只表达「债务清偿完毕」。</p>
     */
    private void clearWidthApproximationDebt() {
        for (int faceIndex = 0; faceIndex < faces.length; faceIndex++) {
            AtomicLongArray debt = faces[faceIndex].widthApproximationDebt;
            if (debt == null) {
                continue;
            }
            for (int index = 0; index < WIDTH_DEBT_WORD_COUNT; index++) {
                debt.set(index, 0L);
            }
        }
        widthApproximationDebtCount.set(0);
    }

    private AtomicLongArray widthApproximationDebtArray(FontType fontType) {
        return forType(fontType).widthApproximationDebt;
    }

    public final float[] widthNormal = createWidthArray();
    public final float[] widthBold = createWidthArray();
    public final int[] matchedFontNormal = createMatchedFontArray();
    public final int[] matchedFontBold = createMatchedFontArray();
    public final byte[] stateNormal = new byte[CODEPOINT_COUNT];
    public final byte[] stateBold = new byte[CODEPOINT_COUNT];
    public final long[] requestIdNormal = new long[CODEPOINT_COUNT];
    public final long[] requestIdBold = new long[CODEPOINT_COUNT];
    public final int[] locationNormal = createLocationArray();
    public final int[] locationBold = createLocationArray();
    public final byte[] flagsNormal = new byte[CODEPOINT_COUNT];
    public final byte[] flagsBold = new byte[CODEPOINT_COUNT];
    public final int[] slotXNormal = new int[CODEPOINT_COUNT];
    public final int[] slotXBold = new int[CODEPOINT_COUNT];
    public final int[] slotYNormal = new int[CODEPOINT_COUNT];
    public final int[] slotYBold = new int[CODEPOINT_COUNT];
    public final int[] slotWidthNormal = new int[CODEPOINT_COUNT];
    public final int[] slotWidthBold = new int[CODEPOINT_COUNT];
    public final int[] slotHeightNormal = new int[CODEPOINT_COUNT];
    public final int[] slotHeightBold = new int[CODEPOINT_COUNT];
    public final int[] atlasBaselineXNormal = new int[CODEPOINT_COUNT];
    public final int[] atlasBaselineXBold = new int[CODEPOINT_COUNT];
    public final int[] atlasBaselineYNormal = new int[CODEPOINT_COUNT];
    public final int[] atlasBaselineYBold = new int[CODEPOINT_COUNT];
    /**
     * 默认字符格内文本基线 Y，量纲=atlas 像素（awtCharSize 坐标系）。
     */
    public final int[] lineBaselineYNormal = new int[CODEPOINT_COUNT];
    /**
     * 默认字符格内文本基线 Y，量纲=atlas 像素（awtCharSize 坐标系）。
     */
    public final int[] lineBaselineYBold = new int[CODEPOINT_COUNT];
    /**
     * 字体上升量，量纲=atlas 像素（awtCharSize 坐标系）。
     */
    public float ascentNormal;
    /**
     * 字体上升量（粗体），量纲=atlas 像素（awtCharSize 坐标系）。
     */
    public float ascentBold;
    /**
     * 字体下降量，量纲=atlas 像素（awtCharSize 坐标系）。
     */
    public float descentNormal;
    /**
     * 字体下降量（粗体），量纲=atlas 像素（awtCharSize 坐标系）。
     */
    public float descentBold;
    /**
     * 字体行间隙，量纲=atlas 像素（awtCharSize 坐标系）。
     */
    public float leadingNormal;
    /**
     * 字体行间隙（粗体），量纲=atlas 像素（awtCharSize 坐标系）。
     */
    public float leadingBold;
    /**
     * x-height（小写 x ink 高），量纲=atlas 像素（awtCharSize 坐标系）；TeX 布局关键参数。
     */
    public float xHeightNormal;
    /**
     * x-height（粗体），量纲=atlas 像素（awtCharSize 坐标系）。
     */
    public float xHeightBold;
    public final short[] inkWidthNormal = new short[CODEPOINT_COUNT];
    public final short[] inkWidthBold = new short[CODEPOINT_COUNT];
    public final short[] inkHeightNormal = new short[CODEPOINT_COUNT];
    public final short[] inkHeightBold = new short[CODEPOINT_COUNT];
    public final short[] bearingXNormal = new short[CODEPOINT_COUNT];
    public final short[] bearingXBold = new short[CODEPOINT_COUNT];
    public final short[] bearingYNormal = new short[CODEPOINT_COUNT];
    public final short[] bearingYBold = new short[CODEPOINT_COUNT];

    public GlyphPage[] normalPages = new GlyphPage[4];
    public GlyphPage[] boldPages = new GlyphPage[4];
    public int normalPageCount;
    public int boldPageCount;
    /**
     * 遗留口径：固定网格时代的单页槽位预算（textureSize/glyphSize 网格计数）。
     *
     * <p>skyline 紧密排列下单页槽位数随字形尺寸分布变化，实际统计见
     * {@code GlyphPageManager.getMaxCommittedSlotsPerPage()}；本字段仅保留兼容。</p>
     */
    public int slotsPerPage;

    /**
     * 字面表，按 {@code FontType.ordinal()} 索引（NORMAL/BOLD/ITALIC/BOLD_ITALIC）。
     *
     * <p>过渡期：NORMAL/BOLD 别名既有 {@code *Normal/*Bold} 数组（单一数据源），
     * ITALIC/BOLD_ITALIC 为新增独立数组。新代码统一走 {@link #forType(FontType)}。</p>
     */
    public final FaceTables[] faces = new FaceTables[FontType.values().length];
    {
        FaceTables normal = new FaceTables();
        normal.width = widthNormal;
        normal.matchedFont = matchedFontNormal;
        normal.state = stateNormal;
        normal.requestId = requestIdNormal;
        normal.location = locationNormal;
        normal.flags = flagsNormal;
        normal.slotX = slotXNormal;
        normal.slotY = slotYNormal;
        normal.slotWidth = slotWidthNormal;
        normal.slotHeight = slotHeightNormal;
        normal.atlasBaselineX = atlasBaselineXNormal;
        normal.atlasBaselineY = atlasBaselineYNormal;
        normal.lineBaselineY = lineBaselineYNormal;
        normal.inkWidth = inkWidthNormal;
        normal.inkHeight = inkHeightNormal;
        normal.bearingX = bearingXNormal;
        normal.bearingY = bearingYNormal;
        normal.ascent = ascentNormal;
        normal.descent = descentNormal;
        normal.leading = leadingNormal;
        normal.xHeight = xHeightNormal;
        normal.pages = normalPages;
        normal.pageCount = normalPageCount;
        normal.widthApproximationDebt = widthApproximationDebtNormal;
        normal.markAliased();

        FaceTables bold = new FaceTables();
        bold.width = widthBold;
        bold.matchedFont = matchedFontBold;
        bold.state = stateBold;
        bold.requestId = requestIdBold;
        bold.location = locationBold;
        bold.flags = flagsBold;
        bold.slotX = slotXBold;
        bold.slotY = slotYBold;
        bold.slotWidth = slotWidthBold;
        bold.slotHeight = slotHeightBold;
        bold.atlasBaselineX = atlasBaselineXBold;
        bold.atlasBaselineY = atlasBaselineYBold;
        bold.lineBaselineY = lineBaselineYBold;
        bold.inkWidth = inkWidthBold;
        bold.inkHeight = inkHeightBold;
        bold.bearingX = bearingXBold;
        bold.bearingY = bearingYBold;
        bold.ascent = ascentBold;
        bold.descent = descentBold;
        bold.leading = leadingBold;
        bold.xHeight = xHeightBold;
        bold.pages = boldPages;
        bold.pageCount = boldPageCount;
        bold.widthApproximationDebt = widthApproximationDebtBold;
        bold.markAliased();

        faces[FontType.NORMAL.ordinal()] = normal;
        faces[FontType.BOLD.ordinal()] = bold;
        // ITALIC/BOLD_ITALIC 惰性分配：首次取用时才付 ~65MiB/张的直索引表成本。
        faces[FontType.ITALIC.ordinal()] = new FaceTables();
        faces[FontType.BOLD_ITALIC.ordinal()] = new FaceTables();
    }

    /**
     * 取某字面的表；{@code null} 视为 NORMAL。未分配字面（ITALIC/BOLD_ITALIC 尚未用到）
     * 在此首次访问时惰性分配，保证调用方拿到的数组恒非 null。
     */
    public FaceTables forType(FontType fontType) {
        FaceTables face = fontType == null ? faces[FontType.NORMAL.ordinal()]
                : faces[clampOrdinal(fontType)];
        if (!face.isAllocated()) {
            synchronized (face) {
                face.allocate(CODEPOINT_COUNT);
            }
        }
        return face;
    }

    private int clampOrdinal(FontType fontType) {
        int index = fontType.ordinal();
        return index >= 0 && index < faces.length ? index : FontType.NORMAL.ordinal();
    }

    /**
     * 判断码点是否可作为 direct-index 下标。
     *
     * @param codepoint 字符码点
     * @return 是否有效
     */
    public static boolean isValidCodepoint(int codepoint) {
        return codepoint >= 0 && codepoint < CODEPOINT_COUNT;
    }

    /**
     * 将页索引和槽位索引打包为单个 int。
     *
     * @param pageIndex 页索引
     * @param slotIndex 槽位索引
     * @return packed location
     */
    public static int packLocation(int pageIndex, int slotIndex) {
        return (pageIndex << 16) | (slotIndex & 0xFFFF);
    }

    public static int unpackPageIndex(int packedLocation) {
        return packedLocation >>> 16;
    }

    public static int unpackSlotIndex(int packedLocation) {
        return packedLocation & 0xFFFF;
    }

    /** 请求 key 中字面类型占用的位数（NORMAL/BOLD/ITALIC/BOLD_ITALIC 共 4 值）。 */
    public static final int REQUEST_TYPE_BITS = 2;

    /**
     * 把 {@code generation + codepoint + FontType} 打包为稳定请求 key。
     *
     * <p>布局：generation(32b) | codepoint(21b) | fontType(2b)。字面占 2 位以容纳
     * 四个 {@link FontType} 取值；旧实现只占 1 位（BOLD/NORMAL），斜体字面会与正体撞 key。</p>
     *
     * @param generation 字体运行时版本
     * @param codepoint  字符码点
     * @param fontType   字面类型；{@code null} 视为 NORMAL
     * @return 稳定请求 key
     */
    public static long packRequestKey(int generation, int codepoint, FontType fontType) {
        int typeBits = fontType == null ? 0 : fontType.ordinal();
        return ((long) generation & 0xFFFFFFFFL) << 32
                | (long) (codepoint & 0x1FFFFFL) << REQUEST_TYPE_BITS
                | (typeBits & 0x3L);
    }

    /** 从 {@link #packRequestKey} 产物解出字面类型。 */
    public static FontType unpackRequestFontType(long requestKey) {
        return FontType.values()[(int) (requestKey & 0x3L)];
    }

    /** 从 {@link #packRequestKey} 产物解出码点。 */
    public static int unpackRequestCodepoint(long requestKey) {
        return (int) ((requestKey >>> REQUEST_TYPE_BITS) & 0x1FFFFFL);
    }

    /**
     * 把装配期 AWT advance 归一化写入宽度缓存（与 {@code TextLayoutService#measureAwtWidth} 同式）。
     *
     * <p>{@code GlyphGenerator} 光栅化时已算出同字体、同 {@code getGlyphSize()} 尺寸的
     * {@code TextLayout.getAdvance()}，此前只进 {@code GlyphInfo} 即丢弃。写入宽度缓存后，
     * 冷启动布局无需再做 AWT 测量，也就不会撞上 {@code widthCacheMissBudgetPerWindow} 的
     * 「按空格宽近似」分支（CJK 被低估 2 倍以上 → 列宽算窄、文字压过表格竖线，
     * 且错误布局被缓存到下一次重布局）。</p>
     *
     * <p>缓存已有值（已测量）时保留原值：测量是权威来源，装配值只填空缺。</p>
     *
     * @param fontType  字重
     * @param codepoint 码点
     * @param advance   装配期 AWT advance（getGlyphSize() 像素坐标系）
     * @param settings  当前 generation 设置；null 或 advance 不可用时跳过
     */
    public void publishAssembledAdvance(FontType fontType, int codepoint, float advance,
            FontRuntimeSettings settings) {
        if (settings == null || !(advance > 0.0F) || !isValidCodepoint(codepoint)) {
            return;
        }
        int glyphSize = settings.getGlyphSize();
        if (glyphSize <= 0) {
            return;
        }
        float[] widthCache = widthArray(fontType);
        if (!Float.isNaN(widthCache[codepoint])) {
            return;
        }
        widthCache[codepoint] = (float) (((double) advance / glyphSize) * settings.getGameCharSize())
                + (float) settings.getCharacterSpacing();
        // 装配真值入缓存即清偿该码点的宽度近似债务：冷启动布局若曾按空格宽近似过，
        // 债务归零时递增宽度收敛代，页面层布局产物随之失效重算。
        clearWidthApproximated(fontType, codepoint);
    }

    public float[] widthArray(FontType fontType) {
        return forType(fontType).width;
    }

    public int[] matchedFontArray(FontType fontType) {
        return forType(fontType).matchedFont;
    }

    public byte[] stateArray(FontType fontType) {
        return forType(fontType).state;
    }

    public long[] requestIdArray(FontType fontType) {
        return forType(fontType).requestId;
    }

    public int[] locationArray(FontType fontType) {
        return forType(fontType).location;
    }

    public byte[] flagsArray(FontType fontType) {
        return forType(fontType).flags;
    }

    public int[] slotXArray(FontType fontType) {
        return forType(fontType).slotX;
    }

    public int[] slotYArray(FontType fontType) {
        return forType(fontType).slotY;
    }

    public int[] slotWidthArray(FontType fontType) {
        return forType(fontType).slotWidth;
    }

    public int[] slotHeightArray(FontType fontType) {
        return forType(fontType).slotHeight;
    }

    public int[] atlasBaselineXArray(FontType fontType) {
        return forType(fontType).atlasBaselineX;
    }

    public int[] atlasBaselineYArray(FontType fontType) {
        return forType(fontType).atlasBaselineY;
    }

    public int[] lineBaselineYArray(FontType fontType) {
        return forType(fontType).lineBaselineY;
    }

    // 行度量：NORMAL/BOLD 的权威存储是遗留 *Normal/*Bold 标量字段（既有读者/测试直接写它们），
    // ITALIC/BOLD_ITALIC 走 FaceTables 标量字段。此处显式分派，不用 forType()——避免把
    // 「标量别名」误当成同源可变引用（Java 字段无法互相别名）。
    public float xHeight(FontType fontType) {
        if (fontType == FontType.BOLD) {
            return xHeightBold;
        }
        if (fontType == FontType.ITALIC) {
            return faces[FontType.ITALIC.ordinal()].xHeight;
        }
        if (fontType == FontType.BOLD_ITALIC) {
            return faces[FontType.BOLD_ITALIC.ordinal()].xHeight;
        }
        return xHeightNormal;
    }

    public float ascent(FontType fontType) {
        if (fontType == FontType.BOLD) {
            return ascentBold;
        }
        if (fontType == FontType.ITALIC) {
            return faces[FontType.ITALIC.ordinal()].ascent;
        }
        if (fontType == FontType.BOLD_ITALIC) {
            return faces[FontType.BOLD_ITALIC.ordinal()].ascent;
        }
        return ascentNormal;
    }

    public float descent(FontType fontType) {
        if (fontType == FontType.BOLD) {
            return descentBold;
        }
        if (fontType == FontType.ITALIC) {
            return faces[FontType.ITALIC.ordinal()].descent;
        }
        if (fontType == FontType.BOLD_ITALIC) {
            return faces[FontType.BOLD_ITALIC.ordinal()].descent;
        }
        return descentNormal;
    }

    public float leading(FontType fontType) {
        if (fontType == FontType.BOLD) {
            return leadingBold;
        }
        if (fontType == FontType.ITALIC) {
            return faces[FontType.ITALIC.ordinal()].leading;
        }
        if (fontType == FontType.BOLD_ITALIC) {
            return faces[FontType.BOLD_ITALIC.ordinal()].leading;
        }
        return leadingNormal;
    }

    public short[] inkWidthArray(FontType fontType) {
        return forType(fontType).inkWidth;
    }

    public short[] inkHeightArray(FontType fontType) {
        return forType(fontType).inkHeight;
    }

    public short[] bearingXArray(FontType fontType) {
        return forType(fontType).bearingX;
    }

    public short[] bearingYArray(FontType fontType) {
        return forType(fontType).bearingY;
    }

    public GlyphPage[] pages(FontType fontType) {
        return forType(fontType).pages;
    }

    public int pageCount(FontType fontType) {
        return forType(fontType).pageCount;
    }

    /**
     * 清空按码点宽度缓存。
     */
    public void clearWidthCache() {
        for (int faceIndex = 0; faceIndex < faces.length; faceIndex++) {
            if (!faces[faceIndex].isAllocated()) {
                continue;
            }
            Arrays.fill(faces[faceIndex].width, Float.NaN);
        }
    }

    /**
     * 清空按码点字体匹配缓存。
     */
    public void clearMatchedFontCache() {
        for (int faceIndex = 0; faceIndex < faces.length; faceIndex++) {
            if (!faces[faceIndex].isAllocated()) {
                continue;
            }
            Arrays.fill(faces[faceIndex].matchedFont, FONT_INDEX_UNRESOLVED);
        }
    }

    /**
     * 清空字形生命周期、位置和页引用。
     */
    public void resetGlyphRuntime() {
        for (int faceIndex = 0; faceIndex < faces.length; faceIndex++) {
            if (!faces[faceIndex].isAllocated()) {
                continue;
            }
            FaceTables face = faces[faceIndex];
            Arrays.fill(face.state, STATE_ABSENT);
            Arrays.fill(face.requestId, 0L);
            Arrays.fill(face.location, LOCATION_NOT_READY);
            Arrays.fill(face.flags, (byte) 0);
        }
        clearMetrics();
        clearGlyphGeometry();
        clearPageReferences();
        clearWidthApproximationDebt();
    }

    /**
     * 惰性清理 generation 生命周期门控所需的表项。
     *
     * <p>只清渲染与请求路径直接作为门控读取的四类数组——state（生命周期）、location
     * （渲染侧直读的定位门控）、width/matchedFont（跨 generation 缓存语义）——加上稳定行度量
     * 与页引用；其余几何/标志数组（slot 系列/baseline/ink/bearing/flags/requestId）保持原值，
     * 靠 location 门控与 generation 校验惰性失效：
     * <ul>
     * <li>渲染侧仅在 location 有效时读取几何数组，而 location 有效必然伴随同批
     *     {@code cacheGlyphGeometry} 先行写入；</li>
     * <li>requestId 由单调递增序列覆写，旧 token 已先被 generation 校验拦截。</li>
     * </ul>
     * 与 {@link #resetGlyphRuntime()}（全量清零）相比，fill 量从约 123MiB 降至约 29MiB，
     * 用于 reload 时避免主线程大数组清理停顿。</p>
     */
    public void resetGlyphLifecycle() {
        for (int faceIndex = 0; faceIndex < faces.length; faceIndex++) {
            if (!faces[faceIndex].isAllocated()) {
                continue;
            }
            FaceTables face = faces[faceIndex];
            Arrays.fill(face.state, STATE_ABSENT);
            Arrays.fill(face.location, LOCATION_NOT_READY);
            Arrays.fill(face.width, Float.NaN);
            Arrays.fill(face.matchedFont, FONT_INDEX_UNRESOLVED);
        }
        clearMetrics();
        clearPageReferences();
        clearWidthApproximationDebt();
    }

    /**
     * 根据当前字形页规格预计算槽位预算（遗留网格口径，紧密排列后仅作参考）。
     *
     * @param columnCount 每页列数
     * @param rowCount    每页行数
     * @param glyphSize   字形格大小
     */
    public void configureSlotCoordinates(int columnCount, int rowCount, int glyphSize) {
        int safeColumnCount = Math.max(1, columnCount);
        int safeRowCount = Math.max(1, rowCount);
        slotsPerPage = safeColumnCount * safeRowCount;
    }

    /**
     * 发布 generation 构建期已经冻结的稳定行度量。
     *
     * @param metrics generation 行度量
     */
    public void setFontMetrics(FontRuntimeMetrics metrics) {
        ascentNormal = metrics.getAscent(FontType.NORMAL);
        descentNormal = metrics.getDescent(FontType.NORMAL);
        leadingNormal = metrics.getLeading(FontType.NORMAL);
        xHeightNormal = metrics.getXHeight(FontType.NORMAL);
        ascentBold = metrics.getAscent(FontType.BOLD);
        descentBold = metrics.getDescent(FontType.BOLD);
        leadingBold = metrics.getLeading(FontType.BOLD);
        xHeightBold = metrics.getXHeight(FontType.BOLD);
        for (FontType fontType : new FontType[] { FontType.ITALIC, FontType.BOLD_ITALIC }) {
            FaceTables face = faces[fontType.ordinal()];
            face.ascent = metrics.getAscent(fontType);
            face.descent = metrics.getDescent(fontType);
            face.leading = metrics.getLeading(fontType);
            face.xHeight = metrics.getXHeight(fontType);
        }
    }

    /** 清零 NORMAL/BOLD 别名度量与斜体字面度量（generation reset 用）。 */
    private void clearMetrics() {
        ascentNormal = 0.0F;
        descentNormal = 0.0F;
        leadingNormal = 0.0F;
        xHeightNormal = 0.0F;
        ascentBold = 0.0F;
        descentBold = 0.0F;
        leadingBold = 0.0F;
        xHeightBold = 0.0F;
        for (FontType fontType : new FontType[] { FontType.ITALIC, FontType.BOLD_ITALIC }) {
            FaceTables face = faces[fontType.ordinal()];
            face.ascent = 0.0F;
            face.descent = 0.0F;
            face.leading = 0.0F;
            face.xHeight = 0.0F;
        }
    }

    /**
     * 确保指定字重的页数组容量足够。
     *
     * @param fontType    字重类型
     * @param minCapacity 最小容量
     */
    public void ensurePageArrayCapacity(FontType fontType, int minCapacity) {
        FaceTables face = forType(fontType);
        if (face.pages.length < minCapacity) {
            face.pages = ensureCapacity(face.pages, minCapacity);
            syncPageAliases();
        }
    }

    public void setPage(FontType fontType, int index, GlyphPage page) {
        ensurePageArrayCapacity(fontType, index + 1);
        FaceTables face = forType(fontType);
        face.pages[index] = page;
        face.pageCount = Math.max(face.pageCount, index + 1);
        syncPageAliases();
    }

    private void clearGlyphGeometry() {
        for (int faceIndex = 0; faceIndex < faces.length; faceIndex++) {
            if (!faces[faceIndex].isAllocated()) {
                continue;
            }
            FaceTables face = faces[faceIndex];
            Arrays.fill(face.slotX, 0);
            Arrays.fill(face.slotY, 0);
            Arrays.fill(face.slotWidth, 0);
            Arrays.fill(face.slotHeight, 0);
            Arrays.fill(face.atlasBaselineX, 0);
            Arrays.fill(face.atlasBaselineY, 0);
            Arrays.fill(face.lineBaselineY, 0);
            Arrays.fill(face.inkWidth, (short) 0);
            Arrays.fill(face.inkHeight, (short) 0);
            Arrays.fill(face.bearingX, (short) 0);
            Arrays.fill(face.bearingY, (short) 0);
        }
    }

    private void clearPageReferences() {
        mathGlyphs.clear();
        for (int faceIndex = 0; faceIndex < faces.length; faceIndex++) {
            FaceTables face = faces[faceIndex];
            Arrays.fill(face.pages, 0, face.pageCount, null);
            face.pageCount = 0;
        }
        syncPageAliases();
    }

    /** 把 NORMAL/BOLD 字面的页数组与计数回写到遗留字段（别名镜像）。 */
    void syncPageAliases() {
        normalPages = faces[FontType.NORMAL.ordinal()].pages;
        normalPageCount = faces[FontType.NORMAL.ordinal()].pageCount;
        boldPages = faces[FontType.BOLD.ordinal()].pages;
        boldPageCount = faces[FontType.BOLD.ordinal()].pageCount;
    }

    private static float[] createWidthArray() {
        float[] widths = new float[CODEPOINT_COUNT];
        Arrays.fill(widths, Float.NaN);
        return widths;
    }

    private static int[] createMatchedFontArray() {
        int[] matchedFonts = new int[CODEPOINT_COUNT];
        Arrays.fill(matchedFonts, FONT_INDEX_UNRESOLVED);
        return matchedFonts;
    }

    private static int[] createLocationArray() {
        int[] locations = new int[CODEPOINT_COUNT];
        Arrays.fill(locations, LOCATION_NOT_READY);
        return locations;
    }

    private static GlyphPage[] ensureCapacity(GlyphPage[] pages, int minCapacity) {
        if (pages.length >= minCapacity) {
            return pages;
        }
        int nextCapacity = pages.length;
        while (nextCapacity < minCapacity) {
            nextCapacity *= 2;
        }
        GlyphPage[] expandedPages = new GlyphPage[nextCapacity];
        System.arraycopy(pages, 0, expandedPages, 0, pages.length);
        return expandedPages;
    }
}
