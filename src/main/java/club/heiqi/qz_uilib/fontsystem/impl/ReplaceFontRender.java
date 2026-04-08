package club.heiqi.qz_uilib.fontsystem.impl;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.eventbus.QZEventBus;
import club.heiqi.qz_uilib.fontsystem.*;
import club.heiqi.qz_uilib.fontsystem.event.FontReloadDoneEvent;
import club.heiqi.qz_uilib.fontsystem.event.FontReloadEvent;
import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import com.ibm.icu.text.Bidi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3d;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class ReplaceFontRender {
    public static Logger LOG = LogManager.getLogger();
    /**公用字体渲染器 - 如有特殊需要请自行创建实例避免污染全局状态(字号)<br>(字符页管理器在本类中是全局单例使用 仅字号颜色等是可实例控制的状态)*/
    public static ReplaceFontRender instance;
    public static ReplaceFontRender getInstance() {
        if (instance == null) {
            Minecraft mc = Minecraft.getMinecraft();
            instance = new ReplaceFontRender();
        }
        return instance;
    }
    /**重载全局指示器*/
    public static boolean inReload = false;
    
    public int[] colorCode;
    public double curCharSize;
    public float saveR, saveG, saveB, saveA;
    public int saveRI, saveGI, saveBI, saveAI;
    public float posX, posY;
    public float FONT_HEIGHT = 8;
    public Random fontRandom = new Random();
    public BatchRenderFont batchRenderer = new BatchRenderFont();
    public SAXParser parser;

    public ReplaceFontRender() {
        curCharSize = Config.charSize;
        // registerResourceManager();
        this.colorCode = new int[]{ 0, 0xAA, 0xAA00, 0xAAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
                0, 0x2A, 0x2A00, 0x2A2A, 0x2A0000, 0x2A002A, 0x2A2A00, 0x2A2A2A, 0x151515, 0x15153F, 0x153F15, 0x153F3F, 0x3F1515, 0x3F153F, 0x3F3F15, 0x3F3F3F};
        try {
            parser = SAXParserFactory.newInstance("renderStringFactor", this.getClass().getClassLoader()).newSAXParser();
        } catch (ParserConfigurationException | SAXException e) {
            LOG.error("创建SAX解析器失败");
            parser = null;
        }
    }

    
    public void onResourceManagerReload(@Nullable IResourceManager p_110549_1_) {
        ReplaceFontRender.reload();
    }
    
    public int drawStringWithShadow(String text, int x, int y, int color) {
        return drawString(text, x, y, color, true);
    }

    
    public int drawString(String text, int x, int y, int color) {
        return drawString(text, x, y, color, false);
    }

    
    public int drawString(String text, int x, int y, int color, boolean dropShadow) {
        if (text.isEmpty()) return x;
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

    
    public int getStringWidth(final String text) {
        if (text == null) return 0;

        int textLength = text.length();
        double width = 0;
        int fontType = PageManager.NORMAL;
        boolean randomStyle = false, boldStyle = false, strikethroughStyle = false, underlineStyle = false, italicStyle;

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
                        randomStyle = false;
                        boldStyle = false;
                        italicStyle = false;
                        underlineStyle = false;
                        strikethroughStyle = false;
                        fontType = PageManager.NORMAL;
                    }
                    // 任何没有见过的操作符都视作重置！
                    default -> {
                        randomStyle = false;
                        boldStyle = false;
                        italicStyle = false;
                        underlineStyle = false;
                        strikethroughStyle = false;
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
                RenderInfo renderInfo;
                // 获取字符页
                renderInfo = PageManager.getInstance().getRenderInfo(codepoint, fontType);
                // 字符页正在生成返回空格
                if (renderInfo == null) {
                    width += Config.spaceWidth;
                } else {
                    CharInfo info = renderInfo.charInfo;  // 流程不错的情况下info不为null
                    width += ((info.advance / info.width) * this.curCharSize) + Config.characterSpacing;
                }

                i += Character.charCount(codepoint);
            }
        }
        return (int) Math.ceil(width);
    }

    
    public int getCharWidth(char character) {
        String s = String.valueOf(character);
        int codepoint = s.codePointAt(0);
        if (s.equals(" ")) return (int) Config.spaceWidth;

        RenderInfo renderInfo = PageManager.getInstance().getRenderInfo(codepoint, PageManager.NORMAL);
        if (renderInfo == null) {
            return (int) Config.spaceWidth;
        }
        CharInfo info = renderInfo.charInfo;
        return (int) Math.ceil(info.advance/info.width*this.curCharSize + Config.characterSpacing);
    }

    public boolean unicodeFlag = true;
    public boolean getUnicodeFlag() {
        return this.unicodeFlag;
    }

    public int textColor = 0xffffffff;
    public void drawSplitString(String str, int x, int y, int wrapWidth, int textColor) {
        this.textColor = textColor;
        str = trimStringNewline(str);
        renderSplitString(str, x, y, wrapWidth, false);
    }

    /**
     * 1.通过指定宽度向字符串内插入换行符 <br/>
     * 2.通过换行符分割字符串为列表
     */
    public List<String> listFormattedStringToWidth(String str, int wrapWidth) {
        return Arrays.asList(wrapFormattedStringToWidth(str, wrapWidth).split("\n"));
    }

    /**
     * 计算字符串换行后的高度
     */
    public int splitStringWidth(String text, int wrapWidth) {
        return (int) Math.ceil(curCharSize * this.listFormattedStringToWidth(text, wrapWidth).size());
    }

    
    public String trimStringToWidth(String text, int targetWidth, boolean b) {
        StringBuilder stringbuilder = new StringBuilder();

        int textLength = text.length();
        double width = 0;
        int fontType = PageManager.NORMAL;
        boolean randomStyle = false, boldStyle = false, strikethroughStyle = false, underlineStyle = false, italicStyle;

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
                        randomStyle = false;
                        boldStyle = false;
                        italicStyle = false;
                        underlineStyle = false;
                        strikethroughStyle = false;
                        fontType = PageManager.NORMAL;
                    }
                    // 任何没有见过的操作符都视作重置！
                    default -> {
                        randomStyle = false;
                        boldStyle = false;
                        italicStyle = false;
                        underlineStyle = false;
                        strikethroughStyle = false;
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


            RenderInfo renderInfo;
            // 获取字符页
            renderInfo = PageManager.getInstance().getRenderInfo(codepoint, fontType);
            // 字符页正在生成返回空格
            if (renderInfo == null) {
                width += Config.spaceWidth;
            }
            else {
                CharInfo info = renderInfo.charInfo;  // info不可为null
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

    
    public String trimStringToWidth(String p_78269_1_, int p_78269_2_) {
        return this.trimStringToWidth(p_78269_1_, p_78269_2_, false);
    }


    public static final String randomSample = "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ!\"#$%&'()*+,-./0123456789:;<=>?" +
            "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~" +
            "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀" +
            "αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■";

    private void collectStringToRender(String text, boolean shadow) {
        int textLength = text.length();

        boolean randomStyle = false, boldStyle = false, strikethroughStyle = false, underlineStyle = false, italicStyle = false;
        int fontType = PageManager.NORMAL;
        int color = saveAI << 24
                | saveRI << 16
                | saveGI << 8
                | saveBI;

        boolean inXML = false;
        StringBuilder xmlCollector = new StringBuilder();
        for (int i = 0; i < textLength;) {
            int codepoint = text.codePointAt(i);

            // 判断该字符是否是操作符 且操作符下一个字符是否存在 1.不存在 2.存在
            // 在字符串末尾没有操作指令但有操作符，结束流程
            if (codepoint == '§' && i == textLength - 1) {
                return;
            }
            // 解析操作指令
            else if (codepoint == '§' && i < textLength - 1) {
                i++;  // 操作符步进
                codepoint = text.codePointAt(i);  // 操作指令
                i++;  // 操作指令步进

                // 执行操作指令
                switch (codepoint) {
                    case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'  -> {
                        randomStyle = false;
                        boldStyle = false;
                        strikethroughStyle = false;
                        underlineStyle = false;
                        italicStyle = false;
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
                        randomStyle = false;
                        boldStyle = false;
                        italicStyle = false;
                        underlineStyle = false;
                        strikethroughStyle = false;
                        fontType = PageManager.NORMAL;
                        color = saveAI << 24
                                | saveRI << 16
                                | saveGI << 8
                                | saveBI;
                    }
                    // 任何没有见过的操作符都视作重置！
                    default -> {
                        randomStyle = false;
                        boldStyle = false;
                        italicStyle = false;
                        underlineStyle = false;
                        strikethroughStyle = false;
                        fontType = PageManager.NORMAL;
                        color = saveAI << 24
                                | saveRI << 16
                                | saveGI << 8
                                | saveBI;
                    }
                }

                // 如果操作指令已经到达末尾结束
                if (i >= textLength) {
                    return;
                }
            }
            else {
                // 检查是否到达末尾
                boolean isLastOne = i >= textLength;
                float width = 0;
                LineInfo lineInfo = new LineInfo().setColor(color);
                RenderInfo renderInfo = PageManager.getInstance().getRenderInfo(codepoint, fontType);
                // 🔛---------- 解析XML 或是直接收集 ----------🔛
                if (inXML) {

                }
                else {
                    // 字符页正在生成返回空格
                    if (renderInfo == null) {
                        width = (float) Config.spaceWidth;
                    }
                    // 字符页不为空
                    else {
                        // 获取字符的信息
                        CharInfo info = renderInfo.charInfo;  // info不可为null

                        // 处理随机化的情况
                        if (randomStyle) {
                            // 直接使用原始的字宽而不是随机的字宽
                            width = (float) (((info.advance / info.width) * this.curCharSize) + Config.characterSpacing);
                            float randomWidth = 0;
                            int randomCharCodepoint;
                            do {
                                int randomIndex = fontRandom.nextInt(randomSample.length());
                                randomCharCodepoint = randomSample.charAt(randomIndex);
                                RenderInfo randomRenderInfo = PageManager.getInstance().getRenderInfo(codepoint, fontType);
                                if (randomRenderInfo == null) continue;  // 没生成好等待 找下一个
                                randomWidth = randomRenderInfo.charInfo.advance;
                            }
                            while (Math.abs(info.advance - randomWidth) > 0.05f);

                            RenderInfo replaceRenderInfo = PageManager.getInstance().getRenderInfo(randomCharCodepoint, PageManager.NORMAL);
                            // 如果随机化的字符页为空回退到原始字符页
                            renderInfo = replaceRenderInfo == null ? renderInfo : replaceRenderInfo;
                            info = replaceRenderInfo == null ? renderInfo.charInfo : replaceRenderInfo.charInfo;
                        }
                        else {
                            width = (float) (((info.advance / info.width) * this.curCharSize) + Config.characterSpacing);
                        }

                        // 🔛---------- 实际收集函数 ----------🔛
                        batchRenderer.collectRender(posX, posY, (float) curCharSize, renderInfo, color, italicStyle);
                        // 🔛---------- 实际收集函数 ----------🔛
                    }
                }


                collectDraw(width, lineInfo, underlineStyle, strikethroughStyle);
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
            boolean enableTexture2D = GL11.glGetBoolean(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(lineColor.x, lineColor.y, lineColor.z, lineColor.w);
            GL11.glBegin(GL11.GL_QUADS);

            for (Vector3d vertex : lineVertex) {
                GL11.glVertex3d(vertex.x, vertex.y, vertex.z);
            }

            GL11.glEnd();
            if (enableTexture2D) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            }
        }

        public LineInfo addVertex(Vector3d v) {
            lineVertex.add(v);
            return this;
        }
    }
    public ArrayList<LineInfo> lineInfos = new ArrayList<>();
    private void collectDraw(float width, LineInfo lineInfo, boolean underlineStyle, boolean strikethroughStyle) {
        if (underlineStyle) {
            lineInfo.addVertex(new Vector3d((this.posX + width), (this.posY + this.curCharSize), 0.0d))
                    .addVertex(new Vector3d((this.posX + width), (this.posY + this.curCharSize - 1.0d), 0.0d))
                    .addVertex(new Vector3d((this.posX), (this.posY + this.curCharSize - 1.0d), 0.0d))
                    .addVertex(new Vector3d((this.posX), (this.posY + this.curCharSize), 0.0d));
            lineInfos.add(lineInfo);
        }
        if (strikethroughStyle) {
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
    public int renderString(String text, int x, int y, int color, boolean shadow) {
        PageManager.getInstance().onMainThread(null);

        float fx = x;
        float fy = y;
        if (text == null) {
            return x;
        }
        else {

            if ((color & 0xfc000000) == 0) {
                color |= 0xff000000;
            }

            if (shadow) {
                color = (color & 0xfcfcfc) >> 2 | color & 0xff000000;
                // color = (color & 0b1111_1100_1111_1100_1111_1100) >> 2 | color & 0b1111_1111_0000_0000_0000_0000_0000_0000;
                fx += Config.shadowOffsetX;
                fy += Config.shadowOffsetY;
            }

            saveAI = (color >> 24 & 255)/* 255 */;
            /* if (saveAI < 80) {
                MyMod.LOG.error("渲染 【{}】 时 是阴影? {}, 透明值过低！", text, shadow);
            } */
            saveRI = (color >> 16 & 255);
            saveGI = (color >> 8 & 255);
            saveBI = (color & 255);

            this.posX = fx;
            this.posY = fy;

            GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
            // 🐕 收集需要渲染的字符 🐱
            batchRenderer.startBatch();

            this.collectStringToRender(text, shadow);

            batchRenderer.endBatch();
            drawCollect();

            if (Config.debugFontRender) {// debug绘制
                GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
                {
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                    GL11.glColor4f(1, 0, 0, 1);
                    GL11.glBegin(GL11.GL_LINES);
                    GL11.glVertex3f(x, y, 0);
                    GL11.glVertex3f(posX, y, 0);
                    GL11.glVertex3f(posX, y, 0);
                    GL11.glVertex3f(posX, (float) (y + curCharSize), 0);
                    GL11.glVertex3f(posX, (float) (y + curCharSize), 0);
                    GL11.glVertex3f(x, (float) (y + curCharSize), 0);
                    GL11.glVertex3f(x, (float) (y + curCharSize), 0);
                    GL11.glVertex3f(x, y, 0);
                    GL11.glEnd();
                }
                GL11.glPopAttrib();
            }


            GL11.glPopAttrib();
            return (int)Math.ceil(this.posX);
        }
    }

    protected InputStream getResourceInputStream(ResourceLocation location) throws IOException {
        return Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream();
    }


    /**去除字符串末尾的所有换行符*/
    public String trimStringNewline(String text) {
        while (text != null && text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    /**自动换行渲染字符串，适配从右向左的文字渲染*/
    public void renderSplitString(String str, int x, int y, int wrapWidth, boolean addShadow) {
        if (str.isEmpty()) return;
        List<String> list = this.listFormattedStringToWidth(str, wrapWidth);

        for (String s1 : list) {
            renderStringAligned(s1, x, y, wrapWidth, this.textColor, addShadow);
            y += (int) Math.ceil(this.curCharSize + Config.lineSpacing);
        }
    }

    /**左对齐方式渲染字符串，适配从右向左的文字渲染*/
    public void renderStringAligned(String s, int x, int y, int wrapWidth, int color, boolean shadow) {
        if (s.isEmpty()) return;

        this.renderString(s, x, y, color, shadow);
    }

    /**将字符串拆分成多行*/
    public String wrapFormattedStringToWidth(String str, int wrapWidth) {
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

            RenderInfo renderInfo = PageManager.getInstance().getRenderInfo(codepoint, PageManager.NORMAL);

            // ----- 获取字符宽度 -----
            if (renderInfo == null) {
                width += Config.spaceWidth;
            }
            else {
                CharInfo info = renderInfo.charInfo;
                width += info.advance / info.width * this.curCharSize + Config.characterSpacing;
            }
            // 到达指定宽度时添加换行并重置宽度计数器
            if (width > wrapWidth) {
                builder.append("\n");
                width = 0;
            }
            // --end 获取字符宽度 end--

            // ----- 添加字符 -----
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

    public Stack<Double> charSizeStack = new Stack<>();
    public void pushCharSize() {
        charSizeStack.push(this.curCharSize);
    }

    public void popCharSize() {
        Double pop = charSizeStack.pop();
        setCharSize(pop);
    }
    public static void reload() {
        // 重载需要注意顺序问题 1.停止当前所有生成任务 2.重置生成配置项 3.清空纹理页
        inReload = true;
        FontReloadEvent event = new FontReloadEvent((float) Config.awtCharSize);
        QZEventBus.getInstance().post(event);

        FontReloadDoneEvent fontReloadDoneEvent = new FontReloadDoneEvent();
        QZEventBus.getInstance().post(fontReloadDoneEvent);
        inReload = false;
    }
}
