package club.heiqi.config.runtime;

/**
 * 提交前自定义校验钩子。
 *
 * <p>在 {@link ConfigManager#save(DraftBuffer)} 中，于 candidate 捕获后、写盘与权威态变更之前
 * 完全锁外调用。入参 {@link DraftView} 是 validator 唯一稳定输入。
 * 返回 {@link ValidationResult#hasErrors()} 为 true 时
 * 保存事务 fail-closed：不修改 Authority、不写盘、不 commit draft、不发布 {@code BATCH_SAVE}。</p>
 *
 * <p>契约：</p>
 * <ul>
 *   <li>实现不得返回 {@code null}；返回 null 时 Manager 视为 INVALID（全局 path {@link #GLOBAL_ERROR_PATH}）。</li>
 *   <li>抛出 {@link RuntimeException} 时 Manager 同样 fail-closed 为 INVALID，不向外传播到提交后阶段。</li>
 *   <li>构造 {@link DraftView} 失败时 Manager 同样 fail-closed。</li>
 *   <li>无自定义逻辑时使用 {@link #noop()}，语义明确，禁止用 null 表示“无校验”。</li>
 *   <li>实现只能读取传入的 DraftView；不得捕获并写来源 manager、DraftBuffer、Authority、
 *       LegacyAdapter，也不得调用同一 manager 的 save/flushRaw。</li>
 *   <li>Manager 不承诺物理阻止上述违约旁路；任何合法并发或旁路修改均按乐观冲突统一处理，
 *       外层保存返回 INVALID 并保留实际修改。</li>
 * </ul>
 *
 * <p>本接口零依赖 uilib，仅依赖同包类型。</p>
 */
public interface DraftValidator {

    /**
     * 全局/跨字段错误使用的稳定 path，供 UI 与测试识别非字段级错误。
     */
    String GLOBAL_ERROR_PATH = "_config";

    /**
     * 校验草稿只读视图。
     *
     * @param draft 待提交草稿的只读快照，非 null
     * @return 校验结果，不得为 null；无错误时返回 {@link ValidationResult#ok()}
     */
    ValidationResult validate(DraftView draft);

    /**
     * 明确的无操作校验器：始终返回 {@link ValidationResult#ok()}。
     *
     * @return 单例 no-op 实现
     */
    static DraftValidator noop() {
        return Noop.INSTANCE;
    }

    /**
     * 无操作实现。
     */
    final class Noop implements DraftValidator {

        static final Noop INSTANCE = new Noop();

        private Noop() {
        }

        @Override
        public ValidationResult validate(DraftView draft) {
            return ValidationResult.ok();
        }
    }
}
