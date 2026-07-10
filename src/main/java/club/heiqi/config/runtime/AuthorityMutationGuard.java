package club.heiqi.config.runtime;

/**
 * Authority / Legacy 写路径在通知期内的 fail-closed 守卫。
 *
 * <p>由 {@link ConfigManager} 在 BATCH_SAVE / RELOAD 通知期间激活；
 * {@link Authority#putRaw} 等 mutation 入口在改内存前调用 {@link #assertWritable()}，
 * 违规抛 {@link ConfigConflictException}（{@link SaveOutcome.ConflictType#SAVE_DURING_NOTIFICATION}），
 * 内存 Authority 零变化。</p>
 */
interface AuthorityMutationGuard {

    /**
     * 断言当前允许写 Authority。
     *
     * @throws ConfigConflictException 通知期内禁止写
     */
    void assertWritable() throws ConfigConflictException;

    /** 始终允许写（默认 / 非通知期）。 */
    AuthorityMutationGuard ALLOW = new AuthorityMutationGuard() {
        @Override
        public void assertWritable() {
            // no-op
        }
    };
}
