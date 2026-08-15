package club.heiqi.uilib.ui.image;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Minecraft item icon 当帧直绘测试：VANILLA 语义断言终态序列与原版一致（保留全部残留）、
 * 2D 物品也走原版委托（不再有复刻序列）；ISOLATED 语义断言入口 GL 状态恢复（含异常路径）；
 * 宿主能力缺失与无效请求的跳过合同。
 */
public class MinecraftItemIconRendererTest {

    // ---------------------------------------------------------------------------------------------
    // zLevel 包装
    // ---------------------------------------------------------------------------------------------

    @Test
    public void shouldUseVisibleGuiDepthAndRestorePreviousValue() {
        RecordingItemDepth depth = new RecordingItemDepth(37.0F);

        MinecraftItemIconRenderer.runWithGuiItemDepth(depth,
                () -> Assert.assertEquals(MinecraftItemIconRenderer.GUI_ITEM_Z_LEVEL,
                        depth.get(), 0.0F));

        Assert.assertEquals(37.0F, depth.get(), 0.0F);
    }

    @Test
    public void shouldRestorePreviousDepthWhenItemRenderFails() {
        RecordingItemDepth depth = new RecordingItemDepth(-12.0F);

        try {
            MinecraftItemIconRenderer.runWithGuiItemDepth(depth,
                    () -> { throw new IllegalStateException("render failed"); });
            Assert.fail("异常应继续传播");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("render failed", expected.getMessage());
        }

        Assert.assertEquals(-12.0F, depth.get(), 0.0F);
    }

    // ---------------------------------------------------------------------------------------------
    // VANILLA 语义：终态序列与原版一致（保留全部残留），2D 物品也走原版委托
    // ---------------------------------------------------------------------------------------------

    /** 纯 2D 物品在 VANILLA 语义下走原版委托（lighting 包装 + delegate），不再有 UILib 自绘复刻序列。 */
    @Test
    public void vanillaPlain2DIconUsesVanillaDelegationWithoutReplication() {
        List<String> events = new ArrayList<String>();
        RecordingGlOps gl = new RecordingGlOps(events);
        RecordingGlAccess glAccess = RecordingGlAccess.defaultState(events);
        RecordingItemDepth depth = new RecordingItemDepth(77.0F);
        RecordingHost host = new RecordingHost(depth, true, glAccess);
        MinecraftItemIconRenderer renderer = new MinecraftItemIconRenderer(RenderSemantics.VANILLA,
                new GlStateScope(glAccess));
        ItemStack stack = new ItemStack(new Item());

        renderer.render(stack, 12, 34, 32, RenderSemantics.VANILLA, host, gl);

        Assert.assertEquals("2D 物品终态序列与原版委托一致（无复刻序列）", Arrays.asList(
                "pushMatrix",
                "translate(12.0,34.0,0.0)",
                "scale(2.0,2.0,1.0)",
                "enableGuiStandardItemLighting",
                "activeTexture(GL_TEXTURE0)",
                "bindTexture2d(7)",
                "matrixMode(GL_MODELVIEW)",
                "clientActiveTexture(GL_TEXTURE0)",
                "disableStandardItemLighting",
                "matrixModeModelView",
                "popMatrix"), events);
        Assert.assertEquals("2D 物品必须委托原版 renderItemAndEffectIntoGUI", 1, host.delegated.size());
        Assert.assertSame(stack, host.delegated.get(0).stack);
        Assert.assertEquals(0, host.delegated.get(0).x);
        Assert.assertEquals(0, host.delegated.get(0).y);
        Assert.assertEquals("VANILLA 不做任何清理，保留 active texture 残留", GL13.GL_TEXTURE0,
                glAccess.activeTexture);
        Assert.assertEquals("VANILLA 保留纹理绑定残留", 7,
                glAccess.textureBindings.get(GL13.GL_TEXTURE0).intValue());
        Assert.assertEquals("VANILLA 保留矩阵模式残留", GL11.GL_MODELVIEW, glAccess.matrixMode);
        Assert.assertEquals("VANILLA 保留 client-active texture 残留", GL13.GL_TEXTURE0,
                glAccess.clientActiveTexture);
        Assert.assertEquals("itemState zLevel 恢复调用前值", 77.0F, depth.get(), 0.0F);
    }

    /** 多 pass 物品与 2D 物品共享同一 VANILLA 核心：终态序列一致，仅委托原版。 */
    @Test
    public void vanillaMultiPassIconUsesSameVanillaDelegationCore() {
        List<String> events = new ArrayList<String>();
        RecordingGlOps gl = new RecordingGlOps(events);
        RecordingGlAccess glAccess = RecordingGlAccess.defaultState(events);
        RecordingItemDepth depth = new RecordingItemDepth(5.0F);
        RecordingHost host = new RecordingHost(depth, true, glAccess);
        MinecraftItemIconRenderer renderer = new MinecraftItemIconRenderer(RenderSemantics.VANILLA,
                new GlStateScope(glAccess));
        ItemStack stack = new ItemStack(new MultiPassItem());

        renderer.render(stack, 8, 9, 16, RenderSemantics.VANILLA, host, gl);

        Assert.assertEquals(Arrays.asList(
                "pushMatrix",
                "translate(8.0,9.0,0.0)",
                "scale(1.0,1.0,1.0)",
                "enableGuiStandardItemLighting",
                "activeTexture(GL_TEXTURE0)",
                "bindTexture2d(7)",
                "matrixMode(GL_MODELVIEW)",
                "clientActiveTexture(GL_TEXTURE0)",
                "disableStandardItemLighting",
                "matrixModeModelView",
                "popMatrix"), events);
        Assert.assertEquals(1, host.delegated.size());
        Assert.assertSame(stack, host.delegated.get(0).stack);
        Assert.assertEquals("itemState zLevel 恢复调用前值", 5.0F, depth.get(), 0.0F);
    }

    /** 宿主无法提供原版委托能力时，VANILLA 语义下跳过绘制且不触碰 GL。 */
    @Test
    public void vanillaWithoutVanillaCapabilitySkipsWithoutGlCalls() {
        List<String> events = new ArrayList<String>();
        RecordingGlOps gl = new RecordingGlOps(events);
        RecordingGlAccess glAccess = RecordingGlAccess.defaultState(events);
        RecordingHost host = new RecordingHost(new RecordingItemDepth(0.0F), false, glAccess);
        MinecraftItemIconRenderer renderer = new MinecraftItemIconRenderer(RenderSemantics.VANILLA,
                new GlStateScope(glAccess));

        renderer.render(new ItemStack(new MultiPassItem()), 8, 9, 16, RenderSemantics.VANILLA, host, gl);

        Assert.assertEquals(0, host.delegated.size());
        Assert.assertTrue(events.isEmpty());
    }

    // ---------------------------------------------------------------------------------------------
    // ISOLATED 语义：入口 GL 状态恢复（含异常路径）
    // ---------------------------------------------------------------------------------------------

    /**
     * ISOLATED 语义：VANILLA 核心外包 GL 状态 scope。原版残留（active/client-active texture、
     * 纹理绑定、矩阵模式）被 finally 恢复；快照（入口）与恢复（出口）动作序列逐一比对。
     */
    @Test
    public void isolatedRestoresEntryGlStateAroundVanillaCore() {
        List<String> events = new ArrayList<String>();
        RecordingGlOps gl = new RecordingGlOps(events);
        RecordingGlAccess glAccess = RecordingGlAccess.defaultState(events);
        RecordingItemDepth depth = new RecordingItemDepth(77.0F);
        RecordingHost host = new RecordingHost(depth, true, glAccess);
        GlStateScope scope = new GlStateScope(glAccess);
        MinecraftItemIconRenderer renderer = new MinecraftItemIconRenderer(RenderSemantics.ISOLATED, scope);

        renderer.render(new ItemStack(new Item()), 12, 34, 32, null, host, gl);

        Assert.assertEquals("入口快照 / 原版核心 / 出口恢复的完整动作序列", Arrays.asList(
                "pushAttrib(GL_ALL_ATTRIB_BITS)",
                "pushClientAttrib(GL_CLIENT_PIXEL_STORE_BIT|GL_CLIENT_VERTEX_ARRAY_BIT)",
                "getInteger(GL_MATRIX_MODE)",
                "getInteger(GL_ACTIVE_TEXTURE)",
                "getInteger(GL_CLIENT_ACTIVE_TEXTURE)",
                "activeTexture(GL_TEXTURE0)",
                "getInteger(GL_TEXTURE_BINDING_2D)",
                "activeTexture(GL_TEXTURE3)",
                "getInteger(GL_TEXTURE_BINDING_2D)",
                "activeTexture(GL_TEXTURE3)",
                "pushMatrix",
                "translate(12.0,34.0,0.0)",
                "scale(2.0,2.0,1.0)",
                "enableGuiStandardItemLighting",
                "activeTexture(GL_TEXTURE0)",
                "bindTexture2d(7)",
                "matrixMode(GL_MODELVIEW)",
                "clientActiveTexture(GL_TEXTURE0)",
                "disableStandardItemLighting",
                "matrixModeModelView",
                "popMatrix",
                "activeTexture(GL_TEXTURE0)",
                "bindTexture2d(42)",
                "activeTexture(GL_TEXTURE3)",
                "bindTexture2d(99)",
                "activeTexture(GL_TEXTURE3)",
                "clientActiveTexture(GL_TEXTURE1)",
                "popClientAttrib",
                "popAttrib",
                "matrixMode(GL_PROJECTION)"), events);
        Assert.assertEquals("active texture 恢复入口值", GL13.GL_TEXTURE3, glAccess.activeTexture);
        Assert.assertEquals("client-active texture 恢复入口值", GL13.GL_TEXTURE1,
                glAccess.clientActiveTexture);
        Assert.assertEquals("unit0 TEXTURE_2D 绑定恢复入口值", 42,
                glAccess.textureBindings.get(GL13.GL_TEXTURE0).intValue());
        Assert.assertEquals("入口 active unit TEXTURE_2D 绑定恢复入口值", 99,
                glAccess.textureBindings.get(GL13.GL_TEXTURE3).intValue());
        Assert.assertEquals("矩阵模式恢复入口值", GL11.GL_PROJECTION, glAccess.matrixMode);
        Assert.assertEquals("绘制核心仍委托原版", 1, host.delegated.size());
        Assert.assertEquals("itemState zLevel 恢复调用前值", 77.0F, depth.get(), 0.0F);
    }

    /** 原版核心抛出异常时，ISOLATED 语义仍在 finally 恢复入口 GL 状态，异常继续传播。 */
    @Test
    public void isolatedRestoresEntryGlStateWhenVanillaCoreThrows() {
        List<String> events = new ArrayList<String>();
        RecordingGlOps gl = new RecordingGlOps(events);
        RecordingGlAccess glAccess = RecordingGlAccess.defaultState(events);
        RecordingItemDepth depth = new RecordingItemDepth(-12.0F);
        RecordingHost host = new RecordingHost(depth, true, glAccess, true);
        GlStateScope scope = new GlStateScope(glAccess);
        MinecraftItemIconRenderer renderer = new MinecraftItemIconRenderer(RenderSemantics.ISOLATED, scope);

        try {
            renderer.render(new ItemStack(new Item()), 12, 34, 32, null, host, gl);
            Assert.fail("异常应继续传播");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("render failed", expected.getMessage());
        }

        Assert.assertEquals("异常路径仍执行出口恢复序列", Arrays.asList(
                "activeTexture(GL_TEXTURE0)",
                "bindTexture2d(42)",
                "activeTexture(GL_TEXTURE3)",
                "bindTexture2d(99)",
                "activeTexture(GL_TEXTURE3)",
                "clientActiveTexture(GL_TEXTURE1)",
                "popClientAttrib",
                "popAttrib",
                "matrixMode(GL_PROJECTION)"), events.subList(events.size() - 9, events.size()));
        Assert.assertEquals("active texture 恢复入口值", GL13.GL_TEXTURE3, glAccess.activeTexture);
        Assert.assertEquals("client-active texture 恢复入口值", GL13.GL_TEXTURE1,
                glAccess.clientActiveTexture);
        Assert.assertEquals("unit0 TEXTURE_2D 绑定恢复入口值", 42,
                glAccess.textureBindings.get(GL13.GL_TEXTURE0).intValue());
        Assert.assertEquals("入口 active unit TEXTURE_2D 绑定恢复入口值", 99,
                glAccess.textureBindings.get(GL13.GL_TEXTURE3).intValue());
        Assert.assertEquals("矩阵模式恢复入口值", GL11.GL_PROJECTION, glAccess.matrixMode);
        Assert.assertTrue("异常路径仍由绘制核心 finally 配对 pop matrix", events.contains("popMatrix"));
        Assert.assertEquals("itemState zLevel 恢复调用前值", -12.0F, depth.get(), 0.0F);
    }

    /** 无效请求与宿主能力缺失在进入 GL 状态 scope 前被拒绝，ISOLATED 下不产生任何 GL 动作。 */
    @Test
    public void isolatedInvalidRequestsTouchNoGlState() {
        List<String> events = new ArrayList<String>();
        RecordingGlOps gl = new RecordingGlOps(events);
        RecordingGlAccess glAccess = RecordingGlAccess.defaultState(events);
        RecordingHost host = new RecordingHost(new RecordingItemDepth(0.0F), true, glAccess);
        GlStateScope scope = new GlStateScope(glAccess);
        MinecraftItemIconRenderer renderer = new MinecraftItemIconRenderer(RenderSemantics.ISOLATED, scope);

        renderer.render(null, 0, 0, 16, null, host, gl);
        renderer.render(new ItemStack(new Item()), 0, 0, 0, null, host, gl);
        renderer.render(new ItemStack(new Item()), 0, 0, -4, null, host, gl);
        renderer.render(new ItemStack(new Item()), 0, 0, 16, null, null, gl);
        renderer.render(new ItemStack(new Item()), 0, 0, 16, null, host, null);
        renderer.render(new ItemStack(new Item()), 0, 0, 16, null,
                new RecordingHost(new RecordingItemDepth(0.0F), false, glAccess), gl);

        Assert.assertTrue(events.isEmpty());
    }

    // ---------------------------------------------------------------------------------------------
    // 默认语义与构造注入
    // ---------------------------------------------------------------------------------------------

    /** 默认构造注入 ISOLATED：无显式语义参数的入口走自净 scope。 */
    @Test
    public void defaultSemanticsIsIsolated() {
        List<String> events = new ArrayList<String>();
        RecordingGlAccess glAccess = RecordingGlAccess.defaultState(events);
        RecordingHost host = new RecordingHost(new RecordingItemDepth(0.0F), true, glAccess);
        GlStateScope scope = new GlStateScope(glAccess);
        MinecraftItemIconRenderer renderer = new MinecraftItemIconRenderer(RenderSemantics.ISOLATED, scope);

        renderer.render(new ItemStack(new Item()), 0, 0, 16, null, host, new RecordingGlOps(events));

        Assert.assertTrue(events.contains("pushAttrib(GL_ALL_ATTRIB_BITS)"));
        Assert.assertTrue(events.contains("popAttrib"));
    }

    /** 构造注入 VANILLA：无显式语义参数的入口保持原版残留，不进入 GL 状态 scope。 */
    @Test
    public void defaultSemanticsIsVanillaWhenInjected() {
        List<String> events = new ArrayList<String>();
        RecordingGlAccess glAccess = RecordingGlAccess.defaultState(events);
        RecordingHost host = new RecordingHost(new RecordingItemDepth(0.0F), true, glAccess);
        GlStateScope scope = new GlStateScope(glAccess);
        MinecraftItemIconRenderer renderer = new MinecraftItemIconRenderer(RenderSemantics.VANILLA, scope);

        renderer.render(new ItemStack(new Item()), 0, 0, 16, null, host, new RecordingGlOps(events));

        Assert.assertFalse(events.contains("pushAttrib(GL_ALL_ATTRIB_BITS)"));
        Assert.assertEquals("VANILLA 保留矩阵模式残留", GL11.GL_MODELVIEW, glAccess.matrixMode);
    }

    @Test
    public void constructorRejectsNullDefaultSemantics() {
        try {
            new MinecraftItemIconRenderer((RenderSemantics) null);
            Assert.fail("expected");
        } catch (IllegalArgumentException expected) {
            Assert.assertEquals("defaultSemantics 不得为 null", expected.getMessage());
        }
    }

    @Test
    public void constructorRejectsNullGlStateScope() {
        try {
            new MinecraftItemIconRenderer(RenderSemantics.ISOLATED, (GlStateScope) null);
            Assert.fail("expected");
        } catch (NullPointerException expected) {
            Assert.assertEquals("glStateScope", expected.getMessage());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 测试基建
    // ---------------------------------------------------------------------------------------------

    /** 多 pass 物品假实现。 */
    private static final class MultiPassItem extends Item {
        @Override
        public boolean requiresMultipleRenderPasses() {
            return true;
        }
    }

    /** 记录绘制核心调用序列的 GL 操作面。 */
    private static final class RecordingGlOps implements MinecraftItemIconRenderer.ItemGlOps {

        private final List<String> events;

        private RecordingGlOps(List<String> events) {
            this.events = events;
        }

        @Override
        public void pushMatrix() {
            events.add("pushMatrix");
        }

        @Override
        public void popMatrix() {
            events.add("popMatrix");
        }

        @Override
        public void matrixModeModelView() {
            events.add("matrixModeModelView");
        }

        @Override
        public void translate(float x, float y, float z) {
            events.add(String.format(Locale.ROOT, "translate(%s,%s,%s)", x, y, z));
        }

        @Override
        public void scale(float x, float y, float z) {
            events.add(String.format(Locale.ROOT, "scale(%s,%s,%s)", x, y, z));
        }

        @Override
        public void enableGuiStandardItemLighting() {
            events.add("enableGuiStandardItemLighting");
        }

        @Override
        public void disableStandardItemLighting() {
            events.add("disableStandardItemLighting");
        }
    }

    /** 有状态的 GL 状态 scope 访问面：记录动作序列并模拟 active texture / 纹理绑定 / 矩阵模式状态。 */
    private static final class RecordingGlAccess implements GlStateScope.GlAccess {

        private final List<String> events;
        private final Map<Integer, Integer> textureBindings = new HashMap<Integer, Integer>();
        private int activeTexture;
        private int clientActiveTexture;
        private int matrixMode;

        private RecordingGlAccess(List<String> events, int activeTexture, int clientActiveTexture,
                int matrixMode, int textureBindingOnTexture0, int textureBindingOnActiveTexture) {
            this.events = events;
            this.activeTexture = activeTexture;
            this.clientActiveTexture = clientActiveTexture;
            this.matrixMode = matrixMode;
            this.textureBindings.put(GL13.GL_TEXTURE0, textureBindingOnTexture0);
            this.textureBindings.put(activeTexture, textureBindingOnActiveTexture);
        }

        /** 非平凡入口态：active=GL_TEXTURE3、client-active=GL_TEXTURE1、矩阵模式=PROJECTION。 */
        private static RecordingGlAccess defaultState(List<String> events) {
            return new RecordingGlAccess(events, GL13.GL_TEXTURE3, GL13.GL_TEXTURE1,
                    GL11.GL_PROJECTION, 42, 99);
        }

        @Override
        public void pushAttrib(int mask) {
            events.add("pushAttrib(" + glName(mask) + ")");
        }

        @Override
        public void popAttrib() {
            events.add("popAttrib");
        }

        @Override
        public void pushClientAttrib(int mask) {
            events.add("pushClientAttrib(" + glName(mask) + ")");
        }

        @Override
        public void popClientAttrib() {
            events.add("popClientAttrib");
        }

        @Override
        public int getInteger(int name) {
            events.add("getInteger(" + glName(name) + ")");
            if (name == GL11.GL_MATRIX_MODE) {
                return matrixMode;
            }
            if (name == GL13.GL_ACTIVE_TEXTURE) {
                return activeTexture;
            }
            if (name == GL13.GL_CLIENT_ACTIVE_TEXTURE) {
                return clientActiveTexture;
            }
            if (name == GL11.GL_TEXTURE_BINDING_2D) {
                Integer binding = textureBindings.get(activeTexture);
                return binding == null ? 0 : binding.intValue();
            }
            return 0;
        }

        @Override
        public void activeTexture(int unit) {
            activeTexture = unit;
            events.add("activeTexture(" + glName(unit) + ")");
        }

        @Override
        public void bindTexture2d(int texture) {
            textureBindings.put(activeTexture, texture);
            events.add("bindTexture2d(" + texture + ")");
        }

        @Override
        public void clientActiveTexture(int unit) {
            clientActiveTexture = unit;
            events.add("clientActiveTexture(" + glName(unit) + ")");
        }

        @Override
        public void matrixMode(int mode) {
            matrixMode = mode;
            events.add("matrixMode(" + glName(mode) + ")");
        }

        /** 模拟原版渲染留下的 GL 残留：换 active/client-active texture、重绑 TEXTURE_2D、改矩阵模式。 */
        void simulateVanillaResidue() {
            activeTexture(GL13.GL_TEXTURE0);
            bindTexture2d(7);
            matrixMode(GL11.GL_MODELVIEW);
            clientActiveTexture(GL13.GL_TEXTURE0);
        }
    }

    /** 记录委托调用、模拟能力缺失与原版 GL 残留的宿主能力面。 */
    private static final class RecordingHost implements MinecraftItemIconRenderer.ItemRenderHost {

        private final MinecraftItemIconRenderer.ItemDepthAccess itemDepth;
        private final boolean canRenderItemAndEffect;
        private final RecordingGlAccess glAccess;
        private final boolean failDelegation;
        private final List<DelegatedCall> delegated = new ArrayList<DelegatedCall>();

        private RecordingHost(MinecraftItemIconRenderer.ItemDepthAccess itemDepth, boolean canRenderItemAndEffect,
                RecordingGlAccess glAccess) {
            this(itemDepth, canRenderItemAndEffect, glAccess, false);
        }

        private RecordingHost(MinecraftItemIconRenderer.ItemDepthAccess itemDepth, boolean canRenderItemAndEffect,
                RecordingGlAccess glAccess, boolean failDelegation) {
            this.itemDepth = itemDepth;
            this.canRenderItemAndEffect = canRenderItemAndEffect;
            this.glAccess = glAccess;
            this.failDelegation = failDelegation;
        }

        @Override
        public MinecraftItemIconRenderer.ItemDepthAccess getItemDepth() {
            return itemDepth;
        }

        @Override
        public boolean canRenderItemAndEffect() {
            return canRenderItemAndEffect;
        }

        @Override
        public void renderItemAndEffectIntoGUI(ItemStack itemStack, int x, int y) {
            if (failDelegation) {
                throw new IllegalStateException("render failed");
            }
            delegated.add(new DelegatedCall(itemStack, x, y));
            if (glAccess != null) {
                glAccess.simulateVanillaResidue();
            }
        }
    }

    /** 原版委托调用记录。 */
    private static final class DelegatedCall {
        private final ItemStack stack;
        private final int x;
        private final int y;

        private DelegatedCall(ItemStack stack, int x, int y) {
            this.stack = stack;
            this.x = x;
            this.y = y;
        }
    }

    /** 纯数值 zLevel 状态访问缝。 */
    private static final class RecordingItemDepth implements MinecraftItemIconRenderer.ItemDepthAccess {
        private float zLevel;

        private RecordingItemDepth(float zLevel) {
            this.zLevel = zLevel;
        }

        @Override
        public float get() {
            return zLevel;
        }

        @Override
        public void set(float zLevel) {
            this.zLevel = zLevel;
        }
    }

    /** 把 GL 常量转为可读符号，便于动作序列断言。 */
    private static String glName(int name) {
        if (name == GL11.GL_ALL_ATTRIB_BITS) {
            return "GL_ALL_ATTRIB_BITS";
        }
        if (name == GL11.GL_MATRIX_MODE) {
            return "GL_MATRIX_MODE";
        }
        if (name == GL13.GL_ACTIVE_TEXTURE) {
            return "GL_ACTIVE_TEXTURE";
        }
        if (name == GL13.GL_CLIENT_ACTIVE_TEXTURE) {
            return "GL_CLIENT_ACTIVE_TEXTURE";
        }
        if (name == GL11.GL_TEXTURE_BINDING_2D) {
            return "GL_TEXTURE_BINDING_2D";
        }
        if (name == GL11.GL_MODELVIEW) {
            return "GL_MODELVIEW";
        }
        if (name == GL11.GL_PROJECTION) {
            return "GL_PROJECTION";
        }
        if (name == (GL11.GL_CLIENT_PIXEL_STORE_BIT | GL11.GL_CLIENT_VERTEX_ARRAY_BIT)) {
            return "GL_CLIENT_PIXEL_STORE_BIT|GL_CLIENT_VERTEX_ARRAY_BIT";
        }
        if (name >= GL13.GL_TEXTURE0 && name <= GL13.GL_TEXTURE0 + 31) {
            return "GL_TEXTURE" + (name - GL13.GL_TEXTURE0);
        }
        return String.valueOf(name);
    }
}
