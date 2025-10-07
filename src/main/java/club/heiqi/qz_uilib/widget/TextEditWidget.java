package club.heiqi.qz_uilib.widget;

import club.heiqi.qz_fontrender.fontsystem.impl.ReplaceFontRender;
import club.heiqi.qz_uilib.widget.api.TextEditEvent;
import club.heiqi.qz_uilib.widget.drawUtil.RenderTool;
import club.heiqi.qz_uilib.widget.drawUtil.RoundedRectangle;
import org.joml.Vector2d;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.function.Consumer;

public class TextEditWidget extends Widget implements TextEditEvent {

    public RoundedRectangle outBound = new RoundedRectangle();
    public RoundedRectangle inBound = new RoundedRectangle();

    public String content = "";
    public int currentCursor = 0;
    public long lastCursorShow = System.currentTimeMillis();
    public float textSize = 32;
    public int textColor = 0xffffffff;
    public boolean isFocused = false;

    public TextEditWidget() {

    }

    @Override
    public void drawSelf() {
        super.drawSelf();
        drawTextBox();
        ((ReplaceFontRender)fontRenderer).setCharSize(textSize);
        // 横坐标居中
        float x = (float) (this.x + outBound.factR);
        // float stringWidth = fontRenderer.getStringWidth(content);
        // float x = this.x + (width / 2) - (stringWidth / 2);
        // 纵坐标居中
        float y = this.y + (height / 2) - (textSize / 2);
        fontRenderer.drawString(content, (int) x, (int) y,textColor);
        drawCursor();
    }

    public void drawTextBox() {
        outBound.gen(width,height,32,3,new Vector2d(x,y),0xfff0f0f0);
        inBound.gen(width-5,height-5,32,3,new Vector2d(x+2.5,y+2.5),0xff202020);

        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        RenderTool.getInstance().render(
                outBound.getVertexArray(),
                outBound.getTexCoordArray(),
                outBound.getColorArray(),
                outBound.getIndexArray());

        RenderTool.getInstance().render(
                inBound.getVertexArray(),
                inBound.getTexCoordArray(),
                inBound.getColorArray(),
                inBound.getIndexArray());
    }

    public void drawCursor() {
        if (isFocused) {
            if (System.currentTimeMillis() - lastCursorShow > 1000) {
                lastCursorShow = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - lastCursorShow < 500) {
                ReplaceFontRender fontRender = (ReplaceFontRender) fontRenderer;
                fontRender.setCharSize(textSize);
                String cursorLeft = content.substring(0, currentCursor);
                int stringWidth = fontRender.getStringWidth(cursorLeft);

                // 偏移X坐标
                float x = (float) (this.x + outBound.factR);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex3f(x + stringWidth, y, 0);
                GL11.glVertex3f(x + stringWidth, y + height, 0);
                GL11.glVertex3f(x + stringWidth + 1, y + height, 0);
                GL11.glVertex3f(x + stringWidth + 1, y, 0);
                GL11.glEnd();
            }
        }
    }

    public Consumer<String> textChangeCallBack = (text) -> {};
    public void onTextChange(String text) {
        textChangeCallBack.accept(text);
    }
    public TextEditWidget setTextChangeCallBack(Consumer<String> callBack) {
        this.textChangeCallBack = callBack;
        return this;
    }
    public TextEditWidget setContent(String content) {
        this.content = content;
        this.currentCursor = 0;
        return this;
    }

    public void addCharFromCursor(char c) {
        String cursorLeft = content.substring(0, currentCursor);
        String cursorRight = content.substring(currentCursor);
        content = cursorLeft + c + cursorRight;
        currentCursor++;
        this.onTextChange(content);
    }

    public void deleteCharFromCursor() {
        String cursorLeft = content.substring(0, currentCursor);
        String cursorRight = content.substring(currentCursor);
        if (!cursorLeft.isEmpty()) {
            cursorLeft = cursorLeft.substring(0, currentCursor-1);
            currentCursor--;
        }
        content = cursorLeft + cursorRight;
        this.onTextChange(content);
    }

    @Override
    public void onType(char typeChar, int code) {
        super.onType(typeChar, code);
        if (isFocused) {
            switch (code) {
                case Keyboard.KEY_UP -> {
                    currentCursor = 0;
                }
                case Keyboard.KEY_DOWN -> {
                    currentCursor = content.length();
                }
                case Keyboard.KEY_LEFT -> {
                    currentCursor = Math.max(0, currentCursor-1);
                    lastCursorShow = System.currentTimeMillis();
                }
                case Keyboard.KEY_RIGHT -> {
                    currentCursor = Math.min(currentCursor+1, content.length());
                    lastCursorShow = System.currentTimeMillis();
                }
                case Keyboard.KEY_BACK -> {
                    deleteCharFromCursor();
                }
                default -> {
                    int type = Character.getType(typeChar);
                    if (type != Character.CONTROL
                            && type != Character.FORMAT
                            && type != Character.UNASSIGNED
                            && type != Character.PRIVATE_USE
                    ) {
                        addCharFromCursor(typeChar);
                    }
                }
            }
        }
    }

    @Override
    public void onRelease(float x, float y, int buttonID) {
        super.onRelease(x, y, buttonID);
        isFocused = true;
        // 点击位置和当前字符尾部的x距离
        float diff = Float.MAX_VALUE;
        int checkCount = 0;
        for (int i = 0; i <= content.length(); i++) {
            String left = content.substring(0,i);
            int stringWidth = fontRenderer.getStringWidth(left);
            float d = Math.abs((this.x+stringWidth) - x);
            if (d < diff) {
                diff = d;
            }
            else if (d > diff && diff != Float.MAX_VALUE){
                currentCursor = Math.max(0, i - 1);
                break;
            }
            else if (d == 0) {
                currentCursor = i;
            }
            checkCount = i;
        }
        if (checkCount == content.length()) {
            currentCursor = content.length();
        }
    }

    @Override
    public boolean isMouseInBounds(float mouseX, float mouseY) {
        return super.isMouseInBounds(mouseX, mouseY);
    }

    @Override
    public void onPressNotInBoundsPrivate(float x, float y, int buttonID) {
        super.onPressNotInBoundsPrivate(x, y, buttonID);
    }

    @Override
    public void onPressNotInBounds(float x, float y, int buttonID) {
        super.onPressNotInBounds(x, y, buttonID);
        isFocused = false;
    }
}
