package club.heiqi.uilib.ui.image;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Minecraft 的 ItemStack icon-only 当帧直绘委托。
 *
 * <p>分支判定与原版 {@code RenderItem.renderItemIntoGUI}（:426/:473）一致：
 * 3D block（{@code itemSpriteNumber == 0} 且 {@link RenderBlocks#renderItemIn3d}）或多 pass item model
 * 委托持有的原版 {@link RenderItem} 实例 {@code renderItemAndEffectIntoGUI}，保持全部原版行为与 Forge hook；
 * 纯 2D 图标按原版 2D 分支（:528-559）与 {@code renderIcon}（:756-765）等价自绘，结束状态与
 * 原版 2D 分支（:557-559，LIGHTING enable / ALPHA_TEST disable / BLEND disable）一致。
 * 不经过 FBO 栅格化、缓存或占位；overlay（数量/耐久条）保持 icon-only 合同不绘制。</p>
 */
public final class MinecraftItemIconRenderer implements ItemIconRenderer {

    private static final int VANILLA_ITEM_ICON_SIZE = 16;

    /** 与原版 GUI 物品渲染对齐的可见深度。 */
    static final float GUI_ITEM_Z_LEVEL = 100.0F;

    /** 原版 2D 分支 OpenGlHelper.glBlendFunc(770, 771, 1, 0) 常量。 */
    private static final int BLEND_SRC_ALPHA = 770;
    private static final int BLEND_ONE_MINUS_SRC_ALPHA = 771;
    private static final int BLEND_ONE = 1;
    private static final int BLEND_ZERO = 0;

    private RenderItem itemRenderer;

    @Override
    public void render(ItemStack itemStack, int left, int top, int side) {
        if (itemStack == null || itemStack.getItem() == null || side <= 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            // 无宿主渲染上下文（HUD 空能力路径）：跳过绘制，不崩溃。
            return;
        }
        render(itemStack, left, top, side, new MinecraftItemRenderHost(minecraft, getItemRenderer()),
                MinecraftGlItemOps.INSTANCE);
    }

    /** 包内可见测试入口：注入宿主能力面与 GL 操作面，不依赖 Minecraft 单例。 */
    void render(ItemStack itemStack, int left, int top, int side, ItemRenderHost host, ItemGlOps gl) {
        if (itemStack == null || itemStack.getItem() == null || side <= 0 || host == null || gl == null) {
            return;
        }
        ItemState itemState = host.getItemState();
        if (itemState == null) {
            return;
        }
        boolean plain2D = isPlain2DIcon(itemStack);
        final PlainIconPlan plainIcon;
        if (plain2D) {
            TextureManager textureManager = host.getTextureManager();
            if (textureManager == null) {
                // 无法取得 items atlas 能力：跳过绘制，不崩溃。
                return;
            }
            plainIcon = resolvePlain2DIcon(itemStack, textureManager);
            if (plainIcon == null) {
                // 无法取得 atlas 能力：跳过绘制，不崩溃。
                return;
            }
        } else {
            if (!host.canRenderItemAndEffect()) {
                return;
            }
            plainIcon = null;
        }
        float scale = (float) side / (float) VANILLA_ITEM_ICON_SIZE;
        gl.pushMatrix();
        try {
            runWithGuiItemDepth(itemState, () -> {
                gl.translate(left, top, 0.0F);
                gl.scale(scale, scale, 1.0F);
                if (plainIcon != null) {
                    renderPlain2DIcon(itemStack, host.getTextureManager(), itemState, plainIcon, gl);
                } else {
                    gl.enableGuiStandardItemLighting();
                    try {
                        host.renderItemAndEffectIntoGUI(itemStack, 0, 0);
                    } finally {
                        gl.disableStandardItemLighting();
                    }
                }
            });
        } finally {
            gl.matrixModeModelView();
            gl.popMatrix();
        }
    }

    /**
     * 原版 {@code RenderItem.renderItemIntoGUI}（:426/:473）分支判定：
     * 3D block 分支与多 pass 分支均委托原版，其余为纯 2D 图标。
     *
     * @param itemStack 目标物品
     * @return 是否走 Qz 2D 等价自绘路径
     */
    static boolean isPlain2DIcon(ItemStack itemStack) {
        boolean threeDimensional = itemStack.getItemSpriteNumber() == 0
                && RenderBlocks.renderItemIn3d(
                        Block.getBlockFromItem(itemStack.getItem()).getRenderType());
        return !threeDimensional && !itemStack.getItem().requiresMultipleRenderPasses();
    }

    /** 纯 2D 图标解析结果：图标与 items/blocks atlas 位置。 */
    static final class PlainIconPlan {
        final IIcon icon;
        final ResourceLocation atlas;

        private PlainIconPlan(IIcon icon, ResourceLocation atlas) {
            this.icon = icon;
            this.atlas = atlas;
        }
    }

    /**
     * 按原版 2D 分支（RenderItem.java :533-539）解析纯 2D 图标，不触碰任何 GL。
     *
     * @param itemStack 目标物品
     * @param textureManager items atlas 纹理管理器
     * @return 图标解析结果；无法取得 atlas 能力时返回 {@code null}
     */
    static PlainIconPlan resolvePlain2DIcon(ItemStack itemStack, TextureManager textureManager) {
        IIcon icon = itemStack.getIconIndex();
        ResourceLocation atlas = textureManager.getResourceLocation(itemStack.getItemSpriteNumber());
        if (icon != null) {
            return new PlainIconPlan(icon, atlas);
        }
        ITextureObject atlasTexture = textureManager.getTexture(atlas);
        if (!(atlasTexture instanceof TextureMap)) {
            // 无法取得 atlas 能力：跳过绘制，不崩溃。
            return null;
        }
        return new PlainIconPlan(((TextureMap) atlasTexture).getAtlasSprite("missingno"), atlas);
    }

    /** 原版 2D 分支（RenderItem.java :528-559）等价自绘；结束状态与 :557-559 一致。 */
    static void renderPlain2DIcon(ItemStack itemStack, TextureManager textureManager, ItemState itemState,
            PlainIconPlan plan, ItemGlOps gl) {
        gl.disableLighting();
        gl.enableBlend();
        gl.blendFuncSeparate(BLEND_SRC_ALPHA, BLEND_ONE_MINUS_SRC_ALPHA, BLEND_ONE, BLEND_ZERO);
        gl.bindTexture(textureManager, plan.atlas);
        if (itemState.getRenderWithColor()) {
            int tint = itemStack.getItem().getColorFromItemStack(itemStack, 0);
            gl.color4f((float) (tint >> 16 & 255) / 255.0F, (float) (tint >> 8 & 255) / 255.0F,
                    (float) (tint & 255) / 255.0F, 1.0F);
        }
        gl.enableAlphaTest();
        gl.drawIconQuad(plan.icon, itemState.get());
        gl.enableLighting();
        gl.disableAlphaTest();
        gl.disableBlend();
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

    /** 原版 RenderItem 的 zLevel 与染色开关窄访问缝。 */
    interface ItemState extends ItemDepthAccess {

        /** @return 原版 renderWithColor 开关（决定 2D 路径是否做可选染色） */
        boolean getRenderWithColor();
    }

    private RenderItem getItemRenderer() {
        if (itemRenderer == null) {
            itemRenderer = new RenderItem();
        }
        return itemRenderer;
    }

    /** 物品渲染宿主能力面：原版 RenderItem 状态、items atlas 与委托入口。 */
    interface ItemRenderHost {

        /** @return 持有的原版 RenderItem 状态访问缝（zLevel 与 renderWithColor 的权威来源） */
        ItemState getItemState();

        /** @return items atlas 纹理管理器；为空时纯 2D 路径跳过绘制 */
        TextureManager getTextureManager();

        /** @return 是否具备委托原版 renderItemAndEffectIntoGUI 的字体与渲染引擎能力 */
        boolean canRenderItemAndEffect();

        /** 委托原版 renderItemAndEffectIntoGUI（保持 Forge hook 与全部原版行为）。 */
        void renderItemAndEffectIntoGUI(ItemStack itemStack, int x, int y);
    }

    /** 生产宿主能力面：从 Minecraft 运行时与持有的 RenderItem 实例解析。 */
    private static final class MinecraftItemRenderHost implements ItemRenderHost, ItemState {

        private final Minecraft minecraft;
        private final RenderItem itemRenderer;

        private MinecraftItemRenderHost(Minecraft minecraft, RenderItem itemRenderer) {
            this.minecraft = minecraft;
            this.itemRenderer = itemRenderer;
        }

        @Override
        public ItemState getItemState() {
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
        public boolean getRenderWithColor() {
            return itemRenderer.renderWithColor;
        }

        @Override
        public TextureManager getTextureManager() {
            return minecraft.getTextureManager();
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

        void disableLighting();

        void enableLighting();

        void enableBlend();

        void disableBlend();

        void blendFuncSeparate(int sfactorRGB, int dfactorRGB, int sfactorAlpha, int dfactorAlpha);

        void bindTexture(TextureManager textureManager, ResourceLocation texture);

        void enableAlphaTest();

        void disableAlphaTest();

        void color4f(float red, float green, float blue, float alpha);

        void drawIconQuad(IIcon icon, float zLevel);

        void enableGuiStandardItemLighting();

        void disableStandardItemLighting();
    }

    /** 生产 GL 操作面：直接映射 LWJGL、Tessellator 与原版 RenderHelper。 */
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
        public void disableLighting() {
            GL11.glDisable(GL11.GL_LIGHTING);
        }

        @Override
        public void enableLighting() {
            GL11.glEnable(GL11.GL_LIGHTING);
        }

        @Override
        public void enableBlend() {
            GL11.glEnable(GL11.GL_BLEND);
        }

        @Override
        public void disableBlend() {
            GL11.glDisable(GL11.GL_BLEND);
        }

        @Override
        public void blendFuncSeparate(int sfactorRGB, int dfactorRGB, int sfactorAlpha, int dfactorAlpha) {
            GL14.glBlendFuncSeparate(sfactorRGB, dfactorRGB, sfactorAlpha, dfactorAlpha);
        }

        @Override
        public void bindTexture(TextureManager textureManager, ResourceLocation texture) {
            textureManager.bindTexture(texture);
        }

        @Override
        public void enableAlphaTest() {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        }

        @Override
        public void disableAlphaTest() {
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        }

        @Override
        public void color4f(float red, float green, float blue, float alpha) {
            GL11.glColor4f(red, green, blue, alpha);
        }

        @Override
        public void drawIconQuad(IIcon icon, float zLevel) {
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(0.0D, VANILLA_ITEM_ICON_SIZE, zLevel, icon.getMinU(), icon.getMaxV());
            tessellator.addVertexWithUV(VANILLA_ITEM_ICON_SIZE, VANILLA_ITEM_ICON_SIZE, zLevel, icon.getMaxU(),
                    icon.getMaxV());
            tessellator.addVertexWithUV(VANILLA_ITEM_ICON_SIZE, 0.0D, zLevel, icon.getMaxU(), icon.getMinV());
            tessellator.addVertexWithUV(0.0D, 0.0D, zLevel, icon.getMinU(), icon.getMinV());
            tessellator.draw();
        }

        @Override
        public void enableGuiStandardItemLighting() {
            RenderHelper.enableGUIStandardItemLighting();
        }

        @Override
        public void disableStandardItemLighting() {
            RenderHelper.disableStandardItemLighting();
        }
    }
}
