package club.heiqi.config.ui;

import com.github.bsideup.jabel.Desugar;

/**
 * 保存反馈，不可变。承载 {@code ConfigScreen.saveChanges()} 的结局供 UI 消费。
 *
 * <p>由 {@link DraftSignalAdapter#setSaveFeedback} 写入受控 signal，
 * 状态栏/操作栏经 {@code rt.bind} 消费反馈文本与颜色（守 I1）。</p>
 *
 * @param status  反馈状态：NONE 无反馈 / OK 成功 / INVALID 校验失败 / IO_FAILED 写盘失败
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
        IO_FAILED
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
     * @return 是否为错误反馈（INVALID 或 IO_FAILED）
     */
    public boolean isError() {
        return status == Status.INVALID || status == Status.IO_FAILED;
    }

    /**
     * @return 是否无反馈
     */
    public boolean isNone() {
        return status == Status.NONE;
    }
}
