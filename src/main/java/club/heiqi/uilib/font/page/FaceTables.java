package club.heiqi.uilib.font.page;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * 单字面（face）的运行时表：把原先散落在 {@link GlyphRuntimeTables} 里成对的
 * {@code *Normal/*Bold} 字段收拢成「每个字面一份」。
 *
 * <p>字面维度 = 字重 × 斜体（{@code FontType} 的 4 个取值）。{@link GlyphRuntimeTables}
 * 持有 {@code FaceTables[]} 并按 {@code fontType.ordinal()} 取用；新增字面只需多一个元素，
 * 不再复制一份平行数组。</p>
 *
 * <p>字段非 final：迁移过渡期由 {@link GlyphRuntimeTables} 把既有 normal/bold 数组别名进来，
 * 保证单一数据源；斜体字面为独立新数组，构造即为空态。</p>
 */
public final class FaceTables {

    /** 首次分配时的页容量（与迁移前 {@code new GlyphPage[4]} 一致，超量时由管理器扩容）。 */
    public static final int INITIAL_PAGE_CAPACITY = 4;

    /** 宽度近似债务位图字数：{@code CODEPOINT_COUNT} 位 / 64（与 GlyphRuntimeTables 同式）。 */
    public static final int WIDTH_DEBT_WORD_COUNT =
            (GlyphRuntimeTables.CODEPOINT_COUNT + 63) >>> 6;

    // 页面
    public GlyphPage[] pages = new GlyphPage[INITIAL_PAGE_CAPACITY];
    public int pageCount;

    // 宽度 / 字面匹配
    public float[] width;
    public int[] matchedFont;

    // 状态 / 请求
    public byte[] state;
    public long[] requestId;
    public int[] location;
    public byte[] flags;

    // 槽位（atlas 像素）
    public int[] slotX;
    public int[] slotY;
    public int[] slotWidth;
    public int[] slotHeight;

    // 基线（atlas 像素）
    public int[] atlasBaselineX;
    public int[] atlasBaselineY;
    public int[] lineBaselineY;

    // 度量（atlas 像素 / awtCharSize 坐标系）
    public float ascent;
    public float descent;
    public float leading;
    public float xHeight;

    // ink / bearing（texel）
    public short[] inkWidth;
    public short[] inkHeight;
    public short[] bearingX;
    public short[] bearingY;

    /** 宽度近似债务位图（每字面一份）；未分配字面为 null。 */
    public AtomicLongArray widthApproximationDebt;

    /**
     * 直索引数组是否已分配。
     *
     * <p>NORMAL/BOLD 由 {@link GlyphRuntimeTables} 别名既有数组并置本标记；ITALIC/BOLD_ITALIC
     * 初始<b>不分配</b>（4 张约 260MiB，正常只用到 2 张），首次真正取用时经
     * {@link #allocate(int)} 惰性分配。热路径读 volatile 后直接索引，稳态无锁。</p>
     */
    private volatile boolean allocated;

    /** 空字面：直索引数组暂为 null，等待 {@link #allocate(int)} 或别名填充。 */
    public FaceTables() {
    }

    /**
     * 构造并立即分配一个空态字面。
     *
     * @param codepointCount 直索引码点表长度（{@link GlyphRuntimeTables#CODEPOINT_COUNT}）
     */
    public FaceTables(int codepointCount) {
        allocate(codepointCount);
    }

    /** @return 直索引数组是否已就绪 */
    public boolean isAllocated() {
        return allocated;
    }

    /** 标记本字面为「已由外部别名填好」（NORMAL/BOLD 用）。 */
    void markAliased() {
        this.allocated = true;
    }

    /**
     * 分配空态直索引数组：宽度缓存 NaN、匹配缓存 UNRESOLVED、定位 NOT_READY，其余零值。
     * 幂等；已分配则直接返回。
     *
     * @param codepointCount 直索引码点表长度
     */
    public void allocate(int codepointCount) {
        if (allocated) {
            return;
        }
        this.width = new float[codepointCount];
        Arrays.fill(this.width, Float.NaN);
        this.matchedFont = new int[codepointCount];
        Arrays.fill(this.matchedFont, GlyphRuntimeTables.FONT_INDEX_UNRESOLVED);
        this.state = new byte[codepointCount];
        this.requestId = new long[codepointCount];
        this.location = new int[codepointCount];
        Arrays.fill(this.location, GlyphRuntimeTables.LOCATION_NOT_READY);
        this.flags = new byte[codepointCount];
        this.slotX = new int[codepointCount];
        this.slotY = new int[codepointCount];
        this.slotWidth = new int[codepointCount];
        this.slotHeight = new int[codepointCount];
        this.atlasBaselineX = new int[codepointCount];
        this.atlasBaselineY = new int[codepointCount];
        this.lineBaselineY = new int[codepointCount];
        this.inkWidth = new short[codepointCount];
        this.inkHeight = new short[codepointCount];
        this.bearingX = new short[codepointCount];
        this.bearingY = new short[codepointCount];
        this.widthApproximationDebt = new AtomicLongArray(WIDTH_DEBT_WORD_COUNT);
        this.allocated = true;
    }
}
