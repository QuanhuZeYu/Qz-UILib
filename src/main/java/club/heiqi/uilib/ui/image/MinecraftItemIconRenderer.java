package club.heiqi.uilib.ui.image;

import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Minecraft 的 ItemStack icon-only 当帧直绘委托。
 *
 * <p>绘制核心是原版 {@code RenderItem.renderItemAndEffectIntoGUI} 的完整委托（含 matrix/zLevel/lighting
 * 包装与全部 Forge hook）：3D block、多 pass 与纯 2D 图标一律走原版分支（lighting 包装与原版
 * {@code GuiContainer} 槽位绘制一致，包围每一种物品），不再有 UILib 自绘复刻。VANILLA 语义下
 * 渲染结束后的 GL 状态与原版调用逐位一致（保留全部残留）。</p>
 *
 * <p>两种渲染语义（{@link RenderSemantics}）共享同一核心：</p>
 * <ul>
 * <li>{@link RenderSemantics#VANILLA}：直接执行核心，不做任何 GL 清理；</li>
 * <li>{@link RenderSemantics#ISOLATED}（默认）：核心外包 {@link GlStateScope}——入口态快照
 * （attrib + 纹理绑定 + active/client-active texture）并在 {@code finally} 恢复，异常路径同样恢复。</li>
 * </ul>
 *
 * <p>不经过 FBO 栅格化、缓存或占位；overlay（数量/耐久条）保持 icon-only 合同不绘制。</p>
 */
public final class MinecraftItemIconRenderer implements ItemIconRenderer {

    private static final int VANILLA_ITEM_ICON_SIZE = 16;

    /** 与原版 GUI 物品渲染对齐的可见深度。 */
    static final float GUI_ITEM_Z_LEVEL = 100.0F;

    private final RenderSemantics defaultSemantics;
    private final GlStateScope glStateScope;
    private RenderItem itemRenderer;

    /** 创建默认 {@link RenderSemantics#ISOLATED} 语义的渲染器。 */
    public MinecraftItemIconRenderer() {
        this(RenderSemantics.ISOLATED);
    }

    /**
     * 构造注入默认语义。
     *
     * @param defaultSemantics 无显式语义参数调用 {@link #render(ItemStack, int, int, int)} 时使用的语义
     */
    public MinecraftItemIconRenderer(RenderSemantics defaultSemantics) {
        this(defaultSemantics, new GlStateScope());
    }

    /** 包内可见测试入口：注入 GL 状态 scope，不依赖 Minecraft 单例。 */
    MinecraftItemIconRenderer(RenderSemantics defaultSemantics, GlStateScope glStateScope) {
        if (defaultSemantics == null) {
            throw new IllegalArgumentException("defaultSemantics 不得为 null");
        }
        this.defaultSemantics = defaultSemantics;
        this.glStateScope = Objects.requireNonNull(glStateScope, "glStateScope");
    }

    @Override
    public void render(ItemStack itemStack, int left, int top, int side) {
        render(itemStack, left, top, side, defaultSemantics);
    }

    @Override
    public void render(ItemStack itemStack, int left, int top, int side, RenderSemantics semantics) {
        if (itemStack == null || itemStack.getItem() == null || side <= 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            // 无宿主渲染上下文（HUD 空能力路径）：跳过绘制，不崩溃。
            return;
        }
        render(itemStack, left, top, side, resolveSemantics(semantics),
                new MinecraftItemRenderHost(minecraft, getItemRenderer()), MinecraftGlItemOps.INSTANCE);
    }

    private RenderSemantics resolveSemantics(RenderSemantics semantics) {
        return semantics == null ? defaultSemantics : semantics;
    }

    /**
     * 包内可见测试入口：注入宿主能力面与 GL 操作面，并按语义决定是否包 GL 状态 scope。
     *
     * <p>全部参数与宿主能力校验在进入 GL 状态 scope 之前完成，无效请求不触碰任何 GL 状态。</p>
     */
    void render(ItemStack itemStack, int left, int top, int side, RenderSemantics semantics,
            ItemRenderHost host, ItemGlOps gl) {
        if (itemStack == null || itemStack.getItem() == null || side <= 0 || host == null || gl == null) {
            return;
        }
        if (host.getItemDepth() == null || !host.canRenderItemAndEffect()) {
            return;
        }
        Runnable vanillaCore = () -> drawVanillaCore(itemStack, left, top, side, host, gl);
        if (resolveSemantics(semantics) == RenderSemantics.ISOLATED) {
            glStateScope.run(vanillaCore);
        } else {
            vanillaCore.run();
        }
    }

    /**
     * VANILLA 核心：原版 {@code renderItemAndEffectIntoGUI} 的完整委托（含 matrix/zLevel/lighting
     * 包装），渲染后 GL 状态与原版调用逐位一致（保留全部残留），不做任何清理。
     */
    private void drawVanillaCore(ItemStack itemStack, int left, int top, int side, ItemRenderHost host, ItemGlOps gl) {
        ItemDepthAccess itemDepth = host.getItemDepth();
        float scale = (float) side / (float) VANILLA_ITEM_ICON_SIZE;
        gl.pushMatrix();
        try {
            runWithGuiItemDepth(itemDepth, () -> {
                gl.translate(left, top, 0.0F);
                gl.scale(scale, scale, 1.0F);
                gl.enableGuiStandardItemLighting();
                gl.setLightmapTextureCoords(240.0F, 240.0F);
                gl.enableDepthTest();
                try {
                    host.renderItemAndEffectIntoGUI(itemStack, 0, 0);
                } finally {
                    gl.disableStandardItemLighting();
                }
            });
        } finally {
            gl.matrixModeModelView();
            gl.popMatrix();
        }
    }

    /** 在 GUI 可见深度执行物品绘制，并无条件恢复调用前深度。 */
    static void runWithGuiItemDepth(ItemDepthAccess depthAccess, Runnable renderAction) {
        float previousZLevel = depthAccess.get();
        depthAccess.set(GUI_ITEM_Z_LEVEL);
        try {
            renderAction.run();
        } finally {
            depthAccess.set(previousZLevel);
        }
    }

    /** 可在纯 JVM 测试中替换的 zLevel 最小访问缝。 */
    interface ItemDepthAccess {
        float get();
        void set(float zLevel);
    }

    /** 物品渲染宿主能力面：原版 RenderItem 状态与委托入口。 */
    interface ItemRenderHost {

        /** @return 持有的原版 RenderItem zLevel 访问缝（权威来源） */
        ItemDepthAccess getItemDepth();

        /** @return 是否具备委托原版 renderItemAndEffectIntoGUI 的字体与渲染引擎能力 */
        boolean canRenderItemAndEffect();

        /** 委托原版 renderItemAndEffectIntoGUI（保持 Forge hook 与全部原版行为）。 */
        void renderItemAndEffectIntoGUI(ItemStack itemStack, int x, int y);
    }

    /** 生产宿主能力面：从 Minecraft 运行时与持有的 RenderItem 实例解析。 */
    private static final class MinecraftItemRenderHost implements ItemRenderHost, ItemDepthAccess {

        private final Minecraft minecraft;
        private final RenderItem itemRenderer;

        private MinecraftItemRenderHost(Minecraft minecraft, RenderItem itemRenderer) {
            this.minecraft = minecraft;
            this.itemRenderer = itemRenderer;
        }

        @Override
        public ItemDepthAccess getItemDepth() {
            return this;
        }

        @Override
        public float get() {
            return itemRenderer.zLevel;
        }

        @Override
        public void set(float zLevel) {
            itemRenderer.zLevel = zLevel;
        }

        @Override
        public boolean canRenderItemAndEffect() {
            return minecraft.fontRenderer != null && minecraft.getTextureManager() != null;
        }

        @Override
        public void renderItemAndEffectIntoGUI(ItemStack itemStack, int x, int y) {
            itemRenderer.renderItemAndEffectIntoGUI(
                    minecraft.fontRenderer, minecraft.getTextureManager(), itemStack, x, y);
        }
    }

    /** 物品图标绘制的最小 GL 操作面，供纯 JVM 测试记录动作序列。 */
    interface ItemGlOps {

        void pushMatrix();

        void popMatrix();

        void matrixModeModelView();

        void translate(float x, float y, float z);

        void scale(float x, float y, float z);

        void enableGuiStandardItemLighting();

        void setLightmapTextureCoords(float x, float y);

        void enableDepthTest();

        void disableStandardItemLighting();
    }

    /** 生产 GL 操作面：直接映射 LWJGL 与原版 RenderHelper。 */
    private static final class MinecraftGlItemOps implements ItemGlOps {

        private static final MinecraftGlItemOps INSTANCE = new MinecraftGlItemOps();

        @Override
        public void pushMatrix() {
            GL11.glPushMatrix();
        }

        @Override
        public void popMatrix() {
            GL11.glPopMatrix();
        }

        @Override
        public void matrixModeModelView() {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }

        @Override
        public void translate(float x, float y, float z) {
            GL11.glTranslatef(x, y, z);
        }

        @Override
        public void scale(float x, float y, float z) {
            GL11.glScalef(x, y, z);
        }

        @Override
        public void enableGuiStandardItemLighting() {
            RenderHelper.enableGUIStandardItemLighting();
        }

        @Override
        public void setLightmapTextureCoords(float x, float y) {
            // lightmap 满亮坐标手动设置：固定使用 unit1（与原版 GUI 槽位绘制的 lightmap unit 一致），避免依赖 MC 类。
            GL13.glMultiTexCoord2f(GL13.GL_TEXTURE1, x, y);
        }

        @Override
        public void enableDepthTest() {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }

        @Override
        public void disableStandardItemLighting() {
            RenderHelper.disableStandardItemLighting();
        }
    }

    private RenderItem getItemRenderer() {
        if (itemRenderer == null) {
            itemRenderer = new RenderItem();
        }
        return itemRenderer;
    }
}
