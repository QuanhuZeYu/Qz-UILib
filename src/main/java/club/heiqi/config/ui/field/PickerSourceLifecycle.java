package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.config.ui.editor.PickerCandidateSource;

/**
 * PickerSourceLifecycle —— 候选源 SPI 的<b>会话级释放账本</b>（客户端断开 / 世界退出路径）。
 *
 * <h3>为什么需要本类</h3>
 * <p>{@link PickerCandidateSource#release()} 的契约是「客户端断开、世界退出路径」，
 * <b>不在每次关屏调用</b>（见 SPI javadoc）；而 UILib 是唯一能看到「所有交给自己的候选源」的一方
 * （字段侧接线点 {@code SearchPickerFieldSupport#candidateSourceOf}）。没有这本账时
 * {@code release()} 在生产路径上没有调用点：进程级常驻源（如 Miner 的
 * {@code BlockPickerCandidateSource} 单例）的分片缓存与清单快照只能等到 JVM 退出才释放。</p>
 *
 * <h3>生命周期分层（三者不得混用）</h3>
 * <ul>
 *   <li><b>面板内容 Owner</b>（{@code rt.portal(open, …)}）：每次 open 建、关闭释放 —— 只承载派生与订阅；
 *       <b>不得</b>在这里释放候选源（ADR C4：关闭即停算由 owner 作用域保证，而候选源是跨屏常驻对象）；</li>
 *   <li><b>屏幕 Owner</b>（{@code SceneRuntime#__onCleanup}）：屏级资源（{@code PickerIconCache} 与其
 *       {@code PickerIconResolver}）随屏关闭释放；</li>
 *   <li><b>客户端会话</b>（本类）：进程级常驻候选源的缓存与清单快照，随断连 / 退出世界释放一次。</li>
 * </ul>
 *
 * <h3>边界与保证</h3>
 * <ul>
 *   <li>账本持<b>弱引用</b>：不延长任何源的生命周期、不阻止其被回收（有界且可释放）；</li>
 *   <li>同一实例重复登记只记一条（同一源常被多个屏的 registry 引用）；</li>
 *   <li>释放是<b>一次遍历</b>：单个源抛异常不影响其余源（与断连路径其它清理项同口径）；
 *       按 SPI 契约，释放本身幂等且「释放后再次读取仍可用、版本号不回绕」，故重复断连安全；</li>
 *   <li>入口只允许客户端主线程调用（ADR A-01：SPI 全方法主线程 fail-fast）—— 平台侧
 *       （{@code ClientProxy}）先派发到客户端主线程队列再调用本类，见
 *       {@code PickerSourceGuardWiringTest} 的装配期源码守卫。</li>
 * </ul>
 */
public final class PickerSourceLifecycle {

    private static final Logger LOG = LogManager.getLogger("QzUiLib/ConfigUI");

    private static final Object LOCK = new Object();

    /** 弱引用账本：键 = 源实例本身（不延长生命周期）。 */
    private static final Set<PickerCandidateSource> TRACKED =
            Collections.newSetFromMap(new WeakHashMap<PickerCandidateSource, Boolean>());

    private PickerSourceLifecycle() {
    }

    /**
     * 登记一个已交给 UILib 的候选源（字段侧接线点调用；null 忽略，O(1)，不触碰任何候选数据）。
     *
     * @param source 候选源，可为 null
     */
    public static void track(PickerCandidateSource source) {
        if (source == null) {
            return;
        }
        synchronized (LOCK) {
            TRACKED.add(source);
        }
    }

    /**
     * 释放全部已登记候选源 —— <b>唯一的会话级释放点</b>（客户端断开 / 退出世界）。
     *
     * @param reason 释放原因（诊断用，如 {@code "client_disconnect"}）
     * @return 实际调用 {@link PickerCandidateSource#release()} 的源数量
     * @throws IllegalStateException 非客户端主线程调用（fail-fast；不静默跳过释放）
     */
    public static int releaseAll(String reason) {
        PickerSourceGuard.requireMainThread("release");
        List<PickerCandidateSource> snapshot;
        synchronized (LOCK) {
            snapshot = new ArrayList<PickerCandidateSource>(TRACKED);
        }
        int released = 0;
        for (PickerCandidateSource source : snapshot) {
            try {
                source.release();
                released++;
            } catch (RuntimeException exception) {
                LOG.warn("[QzUiLib/ConfigUI] 候选源会话释放失败（继续释放其余源）：reason={}, source={}",
                        reason, source.getClass().getName(), exception);
            }
        }
        if (released > 0) {
            LOG.debug("[QzUiLib/ConfigUI] 候选源会话释放完成：reason={}, released={}",
                    reason, Integer.valueOf(released));
        }
        return released;
    }

    /** @return 当前账本条目数（诊断/守卫探针；弱引用随源被回收自然下降） */
    public static int trackedCount() {
        synchronized (LOCK) {
            return TRACKED.size();
        }
    }

    /** 测试用：清空账本（不改变生产装配路径）。 */
    public static void __resetForTests() {
        synchronized (LOCK) {
            TRACKED.clear();
        }
    }
}
