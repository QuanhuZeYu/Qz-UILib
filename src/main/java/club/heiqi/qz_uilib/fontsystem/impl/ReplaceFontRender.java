package club.heiqi.qz_uilib.fontsystem.impl;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.MyMod;
import club.heiqi.qz_uilib.fontsystem.*;
import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import com.ibm.icu.text.Bidi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import org.joml.Vector3d;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReplaceFontRender extends FontRenderer {
    /**公用字体渲染器 - 如有特殊需要请自行创建实例避免污染全局状态(字号)<br>(字符页管理器在本类中是全局单例使用 仅字号颜色等是可实例控制的状态)*/
    public static ReplaceFontRender instance;
    public static ReplaceFontRender getInstance() {
        if (instance == null) {
            Minecraft mc = Minecraft.getMinecraft();
            instance = new ReplaceFontRender(mc.gameSettings, new ResourceLocation("textures/font/ascii.png"), mc.renderEngine, true);
        }
        return instance;
    }
    public double curCharSize;
    public float saveR, saveG, saveB, saveA;
    public int saveRI, saveGI, saveBI, saveAI;
    public BatchRenderFont batchRenderer = new BatchRenderFont();

    public ReplaceFontRender(GameSettings gameSettings, ResourceLocation location, TextureManager manager, boolean b
    ) {
        super(gameSettings, location, manager, b);
        curCharSize = Config.charSize;
        CharImageGenerator.getInstance().register();
        // registerResourceManager();
    }

    public void registerResourceManager() {
        Minecraft minecraft = Minecraft.getMinecraft();
        try {
            minecraft.mcResourceManager.registerReloadListener(this);
        } catch (Exception e) {
            MyMod.LOG.error("注册资源包重载时出现错误");
        }
    }

    @Override
    public void onResourceManagerReload(@Nullable IResourceManager p_110549_1_) {
        reload(true);
    }
    public void reload(boolean reloadFontManager) {
        PageManager.getInstance().reload((int) (Config.awtCharSize * 64), (int) Config.awtCharSize);
        CharImageGenerator.getInstance().reload(reloadFontManager);
    }


    @Override
    public int drawStringWithShadow(String text, int x, int y, int color) {
        return drawString(text, x, y, color, true);
    }

    @Override
    public int drawString(String text, int x, int y, int color) {
        return drawString(text, x, y, color, false);
    }

    @Override
    public int drawString(String text, int x, int y, int color, boolean dropShadow) {
        if (text.isEmpty()) return 0;
        this.enableAlpha();
        this.resetStyles();
        int xPos;

        if (dropShadow) {
            xPos = this.renderString(text, x, y, color, true);
            xPos = Math.max(xPos, this.renderString(text, x, y, color, false));
        }
        else {
            xPos = this.renderString(text, x, y, color, false);
        }

        return xPos;
    }

    @Override
    public int getStringWidth(final String text) {
        if (text == null) return 0;

        int textLength = text.length();
        double width = 0;
        int fontType = PageManager.NORMAL;

        for (int i = 0; i < textLength;) {
            int codepoint = text.codePointAt(i);

            // 判断该字符是否是操作符 且操作符下一个字符是否存在
            if (codepoint == '§' && i == textLength - 1) {
                break;
            }
            else if (codepoint == '§' && i < textLength - 1) {
                i++;  // 操作符步进
                codepoint = text.codePointAt(i);  // 操作指令
                i++;  // 操作指令步进

                // 执行操作指令
                switch (codepoint) {
                    case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'  -> {

                    }
                    case 'k' -> {
                        randomStyle = true;
                    }
                    case 'l' -> {
                        boldStyle = true;
                        fontType = PageManager.BOLD;
                    }
                    case 'm' -> {
                        strikethroughStyle = true;
                    }
                    case 'n' -> {
                        underlineStyle = true;
                    }
                    case 'o' -> {
                        italicStyle = true;
                    }
                    case 'r' -> {
                        this.resetStyles();
                        fontType = PageManager.NORMAL;
                    }
                    // 任何没有见过的操作符都视作重置！
                    default -> {
                        this.resetStyles();
                        fontType = PageManager.NORMAL;
                    }
                }

                // 提取下一个字符
                if (i < textLength) {
                    codepoint = text.codePointAt(i);
                }
                // 如果已经到达末尾结束
                else {
                    return (int) Math.ceil(width);
                }
            }

            else {
                CharPage page;
                // 获取字符页
                page = PageManager.getInstance().getPage(codepoint, fontType);
                // 字符页正在生成返回空格
                if (page == null) {
                    width += Config.spaceWidth;
                } else {
                    CharInfo info = page.getCharInfo(codepoint);  // 流程不错的情况下info不为null
                    width += ((info.advance / info.width) * this.curCharSize) + Config.characterSpacing;
                }

                i += Character.charCount(codepoint);
            }
        }
        return (int) Math.ceil(width);
    }

    @Override
    public int getCharWidth(char character) {
        String s = String.valueOf(character);
        int codepoint = s.codePointAt(0);
        if (s.equals(" ")) return (int) Config.spaceWidth;

        CharPage page = PageManager.getInstance().getPage(codepoint, 0);
        if (page == null) {
            return (int) Config.spaceWidth;
        }
        CharInfo info = page.getCharInfo(codepoint);
        return (int) Math.ceil(info.advance/info.width*this.curCharSize + Config.characterSpacing);
    }

    @Override
    public boolean getUnicodeFlag() {
        return this.unicodeFlag;
    }

    @Override
    public void drawSplitString(String str, int x, int y, int wrapWidth, int textColor) {
        this.resetStyles();
        this.textColor = textColor;
        str = trimStringNewline(str);
        renderSplitString(str, x, y, wrapWidth, false);
    }

    @Override
    public boolean getBidiFlag() {
        return this.bidiFlag;
    }

    @Override
    public List<String> listFormattedStringToWidth(String str, int wrapWidth) {
        return Arrays.asList(wrapFormattedStringToWidth(str, wrapWidth).split("\n"));
    }

    @Override
    public void setBidiFlag(boolean bidiFlag) {
        this.bidiFlag = bidiFlag;
    }

    @Override
    public void setUnicodeFlag(boolean unicodeFlag) {
        this.unicodeFlag = unicodeFlag;
    }

    @Override
    public int splitStringWidth(String text, int wrapWidth) {
        return (int) Math.ceil(curCharSize * this.listFormattedStringToWidth(text, wrapWidth).size());
    }

    @Override
    public String trimStringToWidth(String text, int targetWidth, boolean b) {
        StringBuilder stringbuilder = new StringBuilder();

        int textLength = text.length();
        double width = 0;
        int fontType = PageManager.NORMAL;

        for (int i = 0; i < textLength;) {
            int codepoint = text.codePointAt(i);

            // 判断该字符是否是操作符 且操作符下一个字符是否存在
            if (codepoint == '§' && i < textLength - 1) {
                i++;  // 操作符步进
                codepoint = text.codePointAt(i);  // 操作指令
                i++;  // 操作指令步进

                // 执行操作指令
                switch (codepoint) {
                    case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'  -> {

                    }
                    case 'k' -> {
                        randomStyle = true;
                    }
                    case 'l' -> {
                        boldStyle = true;
                        fontType = PageManager.BOLD;
                    }
                    case 'm' -> {
                        strikethroughStyle = true;
                    }
                    case 'n' -> {
                        underlineStyle = true;
                    }
                    case 'o' -> {
                        italicStyle = true;
                    }
                    case 'r' -> {
                        this.resetStyles();
                        fontType = PageManager.NORMAL;
                    }
                    // 任何没有见过的操作符都视作重置！
                    default -> {
                        this.resetStyles();
                        fontType = PageManager.NORMAL;
                    }
                }

                // 提取下一个字符
                if (i < textLength) {
                    codepoint = text.codePointAt(i);
                }
                // 如果已经到达末尾结束
                else {
                    break;
                }
            }


            CharPage page;
            // 获取字符页
            page = PageManager.getInstance().getPage(codepoint, fontType);
            // 字符页正在生成返回空格
            if (page == null) {
                width += Config.spaceWidth;
            }
            else {
                CharInfo info = page.getCharInfo(codepoint);  // info不可为null
                width += ((info.advance / info.width) * this.curCharSize) + Config.characterSpacing;
            }

            // 检查长度
            if (width > targetWidth) {
                break;
            }
            else {
                stringbuilder.append(new String(Character.toChars(codepoint)));
            }

            i += Character.charCount(codepoint);
        }

        return stringbuilder.toString();
    }

    @Override
    public String trimStringToWidth(String p_78269_1_, int p_78269_2_) {
        return this.trimStringToWidth(p_78269_1_, p_78269_2_, false);
    }


    public static final String randomSample = "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ!\"#$%&'()*+,-./0123456789:;<=>?" +
            "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~" +
            "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀" +
            "αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■";

    private void renderStringAtPos_Version2(String text, boolean shadow) {
        int textLength = text.length();

        int fontType = PageManager.NORMAL;
        int color = saveAI << 24
                | saveRI << 16
                | saveGI << 8
                | saveBI;

        for (int i = 0; i < textLength;) {
            int codepoint = text.codePointAt(i);

            // 判断该字符是否是操作符 且操作符下一个字符是否存在
            if (codepoint == '§' && i == textLength - 1) {
                return;
            }
            else if (codepoint == '§' && i < textLength - 1) {
                i++;  // 操作符步进
                codepoint = text.codePointAt(i);  // 操作指令
                i++;  // 操作指令步进

                // 执行操作指令
                switch (codepoint) {
                    case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'  -> {
                        this.randomStyle = false;
                        this.boldStyle = false;
                        this.strikethroughStyle = false;
                        this.underlineStyle = false;
                        this.italicStyle = false;
                        int colorIndex = "0123456789abcdefklmnor".indexOf(codepoint);
                        if (shadow) colorIndex = colorIndex + 16;
                        color = colorCode[colorIndex];
                        color = (saveAI << 24) | color;
                    }
                    case 'k' -> {
                        randomStyle = true;
                    }
                    case 'l' -> {
                        boldStyle = true;
                        fontType = PageManager.BOLD;
                    }
                    case 'm' -> {
                        strikethroughStyle = true;
                    }
                    case 'n' -> {
                        underlineStyle = true;
                    }
                    case 'o' -> {
                        italicStyle = true;
                    }
                    case 'r' -> {
                        this.resetStyles();
                        fontType = PageManager.NORMAL;
                        color = saveAI << 24
                                | saveRI << 16
                                | saveGI << 8
                                | saveBI;
                    }
                    // 任何没有见过的操作符都视作重置！
                    default -> {
                        this.resetStyles();
                        fontType = PageManager.NORMAL;
                        color = saveAI << 24
                                | saveRI << 16
                                | saveGI << 8
                                | saveBI;
                    }
                }

                // 提取下一个字符
                if (i < textLength) {

                }
                // 如果已经到达末尾结束
                else {
                    return;
                }
            }
            else {
                float width = 0;
                CharPage page;
                LineInfo lineInfo = new LineInfo().setColor(color);
                // 获取字符页
                page = PageManager.getInstance().getPage(codepoint, fontType);
                // 字符页正在生成返回空格
                if (page == null) {
                    width = (float) Config.spaceWidth;
                } else {
                    // 获取字符的信息
                    CharInfo info = page.getCharInfo(codepoint);  // info不可为null

                    // 处理随机化的情况
                    if (randomStyle) {
                        // 直接使用原始的字宽而不是随机的字宽
                        width = (float) (((info.advance / info.width) * this.curCharSize) + Config.characterSpacing);
                        float randomWidth = 0;
                        int randomCharCodepoint;
                        do {
                            int randomIndex = fontRandom.nextInt(randomSample.length());
                            randomCharCodepoint = randomSample.charAt(randomIndex);
                            CharPage randomPage = PageManager.getInstance().getPage(codepoint, fontType);
                            if (randomPage == null) continue;  // 没生成好等待 找下一个
                            randomWidth = randomPage.getCharInfo(codepoint).advance;
                        }
                        while (Math.abs(info.advance - randomWidth) > 0.05f);

                        CharPage replacePage = PageManager.getInstance().getPage(randomCharCodepoint, PageManager.NORMAL);
                        // 如果随机化的字符页为空回退到原始字符页
                        page = replacePage == null ? page : replacePage;
                        info = replacePage == null ? page.getCharInfo(codepoint) : replacePage.getCharInfo(randomCharCodepoint);
                    }
                    else {
                        width = (float) (((info.advance / info.width) * this.curCharSize) + Config.characterSpacing);
                    }

                    // TODO 实际渲染环节
                    // batchRenderer.collect(posX, posY, curCharWidth, curCharWidth, page, info, color, italicStyle);
                    batchRenderer.collectRender(posX, posY, (float) curCharSize, page, info, color, italicStyle);
                }

                collectDraw(width, lineInfo);
                i += Character.charCount(codepoint);
            }
        }
    }

    public static class LineInfo {
        public ArrayList<Vector3d> lineVertex = new ArrayList<>();
        public Vector4f lineColor = new Vector4f();

        public LineInfo setColor(int color) {
            lineColor.x = (float) ((color >> 16) & 255) / 255;
            lineColor.y = (float) ((color >> 8) & 255) / 255;
            lineColor.z = (float) ((color) & 255) / 255;
            lineColor.w = (float) ((color >> 24) & 255) / 255;
            return this;
        }

        public void draw() {
            GL11.glColor4f(lineColor.x, lineColor.y, lineColor.z, lineColor.w);
            // GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glBegin(GL11.GL_QUADS);

            for (Vector3d vertex : lineVertex) {
                GL11.glVertex3d(vertex.x, vertex.y, vertex.z);
            }

            GL11.glEnd();
        }

        public LineInfo addVertex(Vector3d v) {
            lineVertex.add(v);
            return this;
        }
    }
    public ArrayList<LineInfo> lineInfos = new ArrayList<>();
    private void collectDraw(float width, LineInfo lineInfo) {
        if (this.underlineStyle) {
            lineInfo.addVertex(new Vector3d((this.posX + width), (this.posY + this.curCharSize), 0.0d))
                    .addVertex(new Vector3d((this.posX + width), (this.posY + this.curCharSize - 1.0d), 0.0d))
                    .addVertex(new Vector3d((this.posX), (this.posY + this.curCharSize - 1.0d), 0.0d))
                    .addVertex(new Vector3d((this.posX), (this.posY + this.curCharSize), 0.0d));
            lineInfos.add(lineInfo);
        }
        if (this.strikethroughStyle) {
            lineInfo.addVertex(new Vector3d((this.posX + width), this.posY + (this.curCharSize / 2) + 0.5, 0.0d))
                    .addVertex(new Vector3d((this.posX + width), this.posY + (this.curCharSize / 2) - 0.5, 0.0d))
                    .addVertex(new Vector3d((this.posX), this.posY + (this.curCharSize / 2) - 0.5, 0.0d))
                    .addVertex(new Vector3d((this.posX), this.posY + (this.curCharSize / 2) + 0.5, 0.0d));
            lineInfos.add(lineInfo);
        }

        this.posX += width;
    }

    public void drawCollect() {
        for (LineInfo lineInfo : lineInfos) {
            lineInfo.draw();
        }
        lineInfos.clear();
    }

    private void resetStyles() {
        this.randomStyle = false;
        this.boldStyle = false;
        this.italicStyle = false;
        this.underlineStyle = false;
        this.strikethroughStyle = false;
    }

    private String bidiReorder(String p_147647_1_) {
        try {
            Bidi bidi = new Bidi((new ArabicShaping(8)).shape(p_147647_1_), 127);
            bidi.setReorderingMode(0);
            return bidi.writeReordered(2);
        }
        catch (ArabicShapingException arabicshapingexception) {
            return p_147647_1_;
        }
    }

    /**
     * 返回当前X坐标位置 即光标位置
     */
    private int renderString(String text, int x, int y, int color, boolean shadow) {
        float fx = x;
        float fy = y;
        if (text == null) {
            return 0;
        }
        else {
            if (this.bidiFlag) {
                text = this.bidiReorder(text);
            }

            if ((color & 0xfc000000) == 0) {
                color |= 0xff000000;
            }

            if (shadow) {
                color = (color & 0xfcfcfc) >> 2 | color & 0xff000000;
                // color = (color & 0b1111_1100_1111_1100_1111_1100) >> 2 | color & 0b1111_1111_0000_0000_0000_0000_0000_0000;
                fx += Config.shadowOffsetX;
                fy += Config.shadowOffsetY;
            }

            saveAI = (color >> 24 & 255);
            saveRI = (color >> 16 & 255);
            saveGI = (color >> 8 & 255);
            saveBI = (color & 255);

            setColor(1, 1, 1, 1);
            this.posX = fx;
            this.posY = fy;

            GL11.glEnable(GL11.GL_TEXTURE_2D);

            // 🐕 收集需要渲染的字符 🐱
            this.renderStringAtPos_Version2(text, shadow);
            batchRenderer.flush();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            drawCollect();

            GL11.glEnable(GL11.GL_TEXTURE_2D);

            return (int)Math.ceil(this.posX);
        }
    }










    @Override
    protected void bindTexture(ResourceLocation location) {

    }

    @Override
    protected void enableAlpha() {
        GL11.glEnable(GL11.GL_ALPHA_TEST);
    }

    @Override
    protected InputStream getResourceInputStream(ResourceLocation location) throws IOException {
        return Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream();
    }


    @Override
    protected void setColor(float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
    }






    private String trimStringNewline(String text) {
        while (text != null && text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private void renderSplitString(String str, int x, int y, int wrapWidth, boolean addShadow) {
        List<String> list = this.listFormattedStringToWidth(str, wrapWidth);

        for (String s1 : list) {
            renderStringAligned(s1, x, y, wrapWidth, this.textColor, addShadow);
            y += (int) Math.ceil(this.curCharSize + Config.lineSpacing);
        }
    }

    private void renderStringAligned(String s, int x, int y, int wrapWidth, int color, boolean shadow) {
        if (s.isEmpty()) return;
        if (this.bidiFlag) {
            int i1 = this.getStringWidth(this.bidiReorder(s));
            x = x + wrapWidth - i1;
        }

        this.renderString(s, x, y, color, shadow);
    }

    private String wrapFormattedStringToWidth(String str, int wrapWidth) {
        StringBuilder builder = new StringBuilder();

        float width = 0;
        for (int i = 0; i < str.length();) {
            // 获取字符信息
            int codepoint = str.codePointAt(i);
            int count = Character.charCount(codepoint);
            char[] chars = Character.toChars(codepoint);
            String s = new String(chars);

            // 跳过操作符和对应的char
            if (s.equals("§")) {
                i ++;
                builder.append(s);
                if (i < str.length()) {
                    // 获取字符信息
                    codepoint = str.codePointAt(i);
                    chars = Character.toChars(codepoint);
                    s = new String(chars);
                    builder.append(s);
                    i++;
                }
                continue;
            }

            CharPage page = PageManager.getInstance().getPage(codepoint, PageManager.NORMAL);

            if (page == null) {
                width += Config.spaceWidth;
            }
            else {
                CharInfo info = page.getCharInfo(codepoint);
                width += info.advance / info.width * this.curCharSize + Config.characterSpacing;
            }
            if (width > wrapWidth) {
                builder.append("\n");
                width = 0;
            }
            builder.append(s);
            i += count;
        }

        return builder.toString();
    }

    public void setCharSize(double size) {
        this.curCharSize = size;
        this.FONT_HEIGHT = (int) Math.ceil(curCharSize);
    }

    public void resetCharSize() {
        setCharSize(Config.charSize);
    }
}
