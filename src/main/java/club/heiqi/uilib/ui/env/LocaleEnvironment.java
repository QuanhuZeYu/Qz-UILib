package club.heiqi.uilib.ui.env;

/**
 * 语言环境域 —— 「当前文本语言」及其变更代际的只读端口。
 *
 * <p><b>为什么既有值又有代际</b>：值回答「现在是什么语言」（headless 出图矩阵按语言出图时
 * 需要它），代际回答「语言换过没有」（缓存失效用）。两者不可互替：代际是单调不减的失效信号，
 * 不是可比较的语言标识。</p>
 *
 * <p><b>代际语义</b>：{@link #nameEpoch()} 只在语言码<b>变化</b>时推进；资源包重载但语言未变时
 * 不推进（避免「换资源包 = 文本代际变化」的伪失效）。消费者按帧或按需比对代际值决定是否重建缓存，
 * 读取本身 O(1)。</p>
 *
 * <p><b>缺席值</b>：{@link #languageCode()} = {@code null}（无从判定），
 * {@link #nameEpoch()} = {@code 0}。与今日未安装 {@code LanguageEpochService} 时逐位等价。</p>
 */
public interface LocaleEnvironment {

    /**
     * 缺席实现：语言码不可得、代际恒为 0（永不失效）。
     */
    LocaleEnvironment EMPTY = new LocaleEnvironment() {
        @Override
        public String languageCode() {
            return null;
        }

        @Override
        public long nameEpoch() {
            return 0L;
        }
    };

    /**
     * 当前语言码（如 {@code zh_CN}）。
     *
     * @return 语言码；{@code null} = 无从判定（无客户端实例 / 语言管理器缺席 / 语言码为空）
     */
    String languageCode();

    /**
     * 文本（语言）代际：单调不减，语言码变化时 +1。
     *
     * @return 当前代际
     */
    long nameEpoch();
}
