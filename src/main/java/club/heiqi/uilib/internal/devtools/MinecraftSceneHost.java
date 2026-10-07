package club.heiqi.uilib.internal.devtools;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 开发场景的原版字体与 GUI 回放边界；核心测量/排版/输入均不依赖本适配。 */
final class MinecraftSceneHost implements SceneHost, SceneTextMeasurer, AutoCloseable {
    private final CoreRectangleRenderer rectangles = new CoreRectangleRenderer();
    @Override public void close() { rectangles.close(); }
    private final GuiScreen screen;
    MinecraftSceneHost(GuiScreen screen) { this.screen = screen; }
    @Override public int width() { return screen.width; }
    @Override public int height() { return screen.height; }
    @Override public long metricsRevision() { return CoreLoopBootstrap.metricsRevision(); }
    @Override public TextLayout layout(String text, TextStyle style, int availableWidth) {
        List<TextLayout.Line> lines = new ArrayList<>();
        if (text.isEmpty()) return new TextLayout(0, 0, style, lines);
        if (!style.font.equals("minecraft")) throw new IllegalArgumentException("开发宿主仅安装 minecraft 字体度量");
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        double scale = (double) style.size / font.FONT_HEIGHT;
        int limit = Math.max(1, (int) Math.floor(availableWidth / scale)), width = 0;
        for (String paragraph : text.split("\n", -1)) {
            String remaining = paragraph;
            if (remaining.isEmpty()) { lines.add(new TextLayout.Line("", 0, lines.size() * style.size)); continue; }
            while (!remaining.isEmpty()) {
                String line = font.trimStringToWidth(remaining, limit);
                if (line.isEmpty()) line = remaining.substring(0, remaining.offsetByCodePoints(0, 1));
                lines.add(new TextLayout.Line(line, 0, lines.size() * style.size));
                width = Math.max(width, (int) Math.ceil(font.getStringWidth(line) * scale));
                remaining = remaining.substring(line.length());
            }
        }
        return new TextLayout(width, lines.size() * style.size, style, lines);
    }
    @Override public void replay(PaintPlan plan) {
        // 保存此适配实际触及的状态，不用固定管线属性栈替代明确状态恢复。
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D), alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        IntBuffer previousClip = BufferUtils.createIntBuffer(4); GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, previousClip);
        FloatBuffer previousColor = BufferUtils.createFloatBuffer(4); GL11.glGetFloatv(GL11.GL_CURRENT_COLOR, previousColor);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        try {
            for (PaintCommand command : plan.commands()) {
                clip(command.clip, scissor, previousClip);
                if (command.kind == PaintCommand.Kind.RECT) rectangles.draw(command, screen.width, screen.height);
                else drawText(command);
            }
        } finally {
            GL11.glMatrixMode(mode); GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding);
            GL14.glBlendFuncSeparate(src, dst, srcAlpha, dstAlpha);
            enabled(GL11.GL_BLEND, blend); enabled(GL11.GL_TEXTURE_2D, texture); enabled(GL11.GL_ALPHA_TEST, alpha);
            GL11.glScissor(previousClip.get(0), previousClip.get(1), previousClip.get(2), previousClip.get(3)); enabled(GL11.GL_SCISSOR_TEST, scissor);
            GL11.glColor4f(previousColor.get(0), previousColor.get(1), previousColor.get(2), previousColor.get(3));
        }
    }
    private void drawText(PaintCommand command) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        float scale = (float) command.text.style.size / font.FONT_HEIGHT;
        for (TextLayout.Line line : command.text.lines()) {
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(command.box.x + line.x, command.box.y + line.y, 0); GL11.glScalef(scale, scale, 1);
                font.drawString(line.text, 0, 0, command.text.style.color);
            } finally { GL11.glPopMatrix(); }
        }
    }
    private void clip(LayoutBox logical, boolean outerEnabled, IntBuffer outer) {
        if (logical == null) {
            enabled(GL11.GL_SCISSOR_TEST, outerEnabled);
            if (outerEnabled) GL11.glScissor(outer.get(0), outer.get(1), outer.get(2), outer.get(3));
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        double sx = (double) minecraft.displayWidth / Math.max(1, screen.width), sy = (double) minecraft.displayHeight / Math.max(1, screen.height);
        int left = (int) Math.ceil(logical.x * sx), right = (int) Math.floor((logical.x + logical.width) * sx);
        int bottom = minecraft.displayHeight - (int) Math.floor((logical.y + logical.height) * sy);
        int top = minecraft.displayHeight - (int) Math.ceil(logical.y * sy);
        if (outerEnabled) { left = Math.max(left, outer.get(0)); right = Math.min(right, outer.get(0) + outer.get(2)); bottom = Math.max(bottom, outer.get(1)); top = Math.min(top, outer.get(1) + outer.get(3)); }
        GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
    }
    private static void enabled(int capability, boolean value) { if (value) GL11.glEnable(capability); else GL11.glDisable(capability); }
}
