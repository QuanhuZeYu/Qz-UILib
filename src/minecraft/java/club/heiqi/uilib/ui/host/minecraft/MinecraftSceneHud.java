package club.heiqi.uilib.ui.host.minecraft;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;

/**
 * Forge 1.7.10 被动 HUD：只借用页面会话与 framebuffer 宿主，不建立原生输入联系。
 * 显隐停止呈现而保留页面；世界或连接替换结束旧资源。位置与编辑策略归页面，见 HUD 规格。
 * 构造、登记、域派发和结束均由同一客户端线程执行；业务域与共享字体/图像服务不归本对象。
 */
public final class MinecraftSceneHud implements AutoCloseable {
    private final ReactiveDomain domain;
    private final ScenePage page;
    private final BooleanSupplier visible;
    private final Thread clientThread = Thread.currentThread();
    private final Runnable present = this::present;
    private ScenePageSession session;
    private Object world;
    private Object connection;
    private boolean eligible;
    private boolean forgeRegistered;
    private boolean fmlRegistered;
    private boolean closed;

    public MinecraftSceneHud(ReactiveDomain domain, ScenePage page, BooleanSupplier visible) {
        this.domain = Objects.requireNonNull(domain, "domain");
        this.page = Objects.requireNonNull(page, "page");
        this.visible = Objects.requireNonNull(visible, "visible");
    }

    /** 幂等登记两个所属事件总线；登记不创建页面或 GPU 资源。结束后不能重新登记。 */
    public MinecraftSceneHud register() {
        checkThread();
        if (closed) throw new IllegalStateException("HUD 已结束");
        if (forgeRegistered && fmlRegistered) return this;
        try {
            forgeRegistered = true;
            MinecraftForge.EVENT_BUS.register(this);
            fmlRegistered = true;
            FMLCommonHandler.instance().bus().register(this);
        } catch (RuntimeException | Error failure) {
            try { close(); } catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        return this;
    }

    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Post event) {
        // Forge 的子元素也发送 Post；一个 HUD 帧只属于最终 ALL 阶段。
        if (event.type == RenderGameOverlayEvent.ElementType.ALL) update(true);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        // F1 或菜单期间仍观察断线与世界替换；tick 不创建会话，也不补跑隐藏帧。
        if (event.phase == TickEvent.Phase.END) update(false);
    }

    private void update(boolean render) {
        if (closed) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        update(minecraft.theWorld, minecraft.getNetHandler(),
                minecraft.currentScreen == null && !minecraft.gameSettings.hideGUI, render);
    }

    /** 游戏身份只用于资源代际；不从世界或连接重建页面输入、布局或业务状态。 */
    void update(Object nextWorld, Object nextConnection, boolean gameHudVisible, boolean render) {
        checkThread();
        if (closed) return;
        eligible = nextWorld != null && nextConnection != null && gameHudVisible;
        if (world != nextWorld || connection != nextConnection) {
            world = nextWorld;
            connection = nextConnection;
            endSession();
        }
        if (eligible && render) domain.execute(present);
    }

    private void present() {
        checkThread();
        if (closed || !eligible) return;
        boolean shown = visible.getAsBoolean();
        // 显隐来源是业务回调，也可能同步结束宿主。
        if (!shown || closed || !eligible) return;
        if (session == null) {
            ScenePageSession opened = openSession(domain, page);
            // 页面构造可能同步结束宿主，不能把迟到会话重新挂到已结束对象上。
            if (closed || !eligible) {
                opened.close();
                return;
            }
            session = opened;
        }
        session.frame();
    }

    private static ScenePageSession openSession(ReactiveDomain domain, ScenePage page) {
        MinecraftSceneHost host = new MinecraftSceneHost();
        final boolean[] owned = {false};
        try {
            return ScenePageSession.open(domain, host, host, context -> {
                context.own(host);
                owned[0] = true;
                page.build(context);
            });
        } catch (RuntimeException | Error failure) {
            if (!owned[0]) {
                try { host.close(); } catch (RuntimeException | Error cleanup) {
                    if (cleanup != failure) failure.addSuppressed(cleanup);
                }
            }
            throw failure;
        }
    }

    private void endSession() {
        ScenePageSession closing = session;
        session = null;
        if (closing != null) closing.close();
    }

    private void checkThread() {
        if (Thread.currentThread() != clientThread)
            throw new IllegalStateException("HUD 必须在所属客户端线程执行");
    }

    /** 注销与会话结束均尝试完成；保持首错，重复结束不重复释放。 */
    @Override
    public void close() {
        if (closed) return;
        checkThread();
        closed = true;
        eligible = false;
        world = null;
        connection = null;
        Throwable failure = null;
        if (forgeRegistered) {
            forgeRegistered = false;
            try { MinecraftForge.EVENT_BUS.unregister(this); }
            catch (RuntimeException | Error next) { failure = next; }
        }
        if (fmlRegistered) {
            fmlRegistered = false;
            try { FMLCommonHandler.instance().bus().unregister(this); }
            catch (RuntimeException | Error next) {
                if (failure == null) failure = next; else if (next != failure) failure.addSuppressed(next);
            }
        }
        try { endSession(); } catch (RuntimeException | Error next) {
            if (failure == null) failure = next; else if (next != failure) failure.addSuppressed(next);
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
    }
}
