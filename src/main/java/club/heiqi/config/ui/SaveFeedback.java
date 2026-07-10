package club.heiqi.config.ui;

import club.heiqi.config.runtime.SaveOutcome;
import com.github.bsideup.jabel.Desugar;

/**
 * 保存反馈，不可变。承载 {@code ConfigScreen.saveChanges()} 的结局供 UI 消费。
 *
 * <p>由 {@link DraftSignalAdapter#setSaveFeedback} 写入受控 signal，
 * 状态栏/操作栏经 {@code rt.bind} 消费反馈文本与颜色（守 I1）。</p>
 *
 * @param status  反馈状态：NONE 无反馈 / OK 成功 / INVALID 校验失败 / IO_FAILED 写盘失败 / CONFLICT 冲突
 * @param message 反馈文案，OK 时为成功提示，失败时为原因，NONE 时为空串
 */
@Desugar
public record SaveFeedback(Status status, String message) {

    /** 保存反馈状态 */
    public enum Status {
        /** 无反馈（初始态或已清除） */
        NONE,
        /** 保存成功 */
        OK,
        /** 校验失败，未写盘 */
        INVALID,
        /** 校验通过但写盘失败 */
        IO_FAILED,
        /** 乐观事务 / 通知期冲突（文案用户化，不依赖英文字符串） */
        CONFLICT
    }

    /** 无反馈单例（初始态） */
    public static final SaveFeedback NONE = new SaveFeedback(Status.NONE, "");

    /**
     * 紧凑构造器：null 安全。
     *
     * @param status  反馈状态，null 时按 NONE
     * @param message 反馈文案，null 时按空串
     */
    public SaveFeedback {
        if (status == null) {
            status = Status.NONE;
        }
        if (message == null) {
            message = "";
        }
    }

    /**
     * @return 是否为错误反馈（INVALID / IO_FAILED / CONFLICT）
     */
    public boolean isError() {
        return status == Status.INVALID
                || status == Status.IO_FAILED
                || status == Status.CONFLICT;
    }

    /**
     * @return 是否无反馈
     */
    public boolean isNone() {
        return status == Status.NONE;
    }

    /**
     * 按结构化冲突类型生成中文友好反馈（UI 唯一文案源，禁止匹配英文诊断串）。
     *
     * @param type 冲突类型，null/NONE 时返回 NONE
     * @return 反馈
     */
    public static SaveFeedback forConflict(SaveOutcome.ConflictType type) {
        if (type == null || type == SaveOutcome.ConflictType.NONE) {
            return NONE;
        }
        switch (type) {
            case STALE_DRAFT_BASE:
                return new SaveFeedback(Status.CONFLICT,
                        "配置已被其他地方更新。当前编辑仅供查看；请点击「丢弃编辑并重新加载」后再保存。");
            case AUTHORITY_MODIFIED_DURING_SAVE:
                return new SaveFeedback(Status.CONFLICT,
                        "保存期间配置已被修改。当前编辑仅供查看；请点击「丢弃编辑并重新加载」后再保存。");
            case DRAFT_MODIFIED_DURING_SAVE:
                return new SaveFeedback(Status.CONFLICT,
                        "保存期间草稿被修改，本次未提交。可检查编辑后重试保存。");
            case SAVE_DURING_NOTIFICATION:
                return new SaveFeedback(Status.CONFLICT,
                        "正在处理上一次保存通知，请稍后重试。");
            case DRAFT_OWNER_MISMATCH:
                return new SaveFeedback(Status.CONFLICT,
                        "草稿不属于当前配置管理器，无法保存。请使用本管理器打开的草稿。");
            default:
                return new SaveFeedback(Status.CONFLICT, "保存冲突，请重试或重新加载。");
        }
    }
}
