package club.heiqi.qz_uilib.skija.gui;

import club.heiqi.qz_uilib.skija.alignment.StringAlignUtils;
import club.heiqi.qz_uilib.skija.font.FontLoader;
import club.heiqi.qz_uilib.skija.gui.component.UIComponent;
import club.heiqi.qz_uilib.skija.gui.component.defaultStyle.BackGround;
import club.heiqi.qz_uilib.skija.gui.component.defaultStyle.Button;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import org.joml.Vector2f;
import org.lwjgl.opengl.Display;

public class TestGUI extends BaseGUI {

    @Override
    public void addComponent() {
        // 背景坐标
        float width = 0.8f; float height = 0.8f;
        float x = 0.1f; float y = 0.1f;
        UIComponent background = new BackGround(x,y,width,height).setBlur(true).setTween();
        // 按钮1坐标
        width = 0.18f; height = 0.08f;
        x = 0.1895f; y = 0.7528f;
        UIComponent button1 = new Button(x,y,width,height).setText("取消")
            .setDefaultFillColor(0x00000000).setDefaultStrokeColor(0xFFFF7070)
            .setDefaultHoverFillColor(0x00000000).setDefaultHoverStrokeColor(0xFFffffff)
            .setTween();
        // 按钮2坐标
        x = 0.63f;
        UIComponent button2 = new Button(x,y,width,height).setText("确认").setTween();

        components.add(background);
        components.add(button1);
        components.add(button2);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        int x = mouseInfo.currPos.x; int y = mouseInfo.currPos.y;
        canvas.addRender(canvas1 -> {
            Font font = new Font(FontLoader.getDefaultFont().getTypeface()).setSize(FontLoader.DYNAMIC_FONT_SIZE*Display.getHeight());
            Paint strPaint = new Paint().setColor(0xFFFFFFFF).setAntiAlias(true);
            String debug1 = "鼠标位置:"+x+","+y+" | "+mouseX+","+mouseY;
            String debug2 = "鼠标状态:"+mouseState.name();
            String debug3 = "上个鼠标位置记录:("+mouseInfo.prevPos.x+","+mouseInfo.prevPos.y+") | 当前记录:("+mouseInfo.currPos.x+","+mouseInfo.currPos.y+")";
            float leading = font.getMetrics().getLeading();
            float fontHeight = font.getMetrics().getHeight();
            float spacing = leading+fontHeight;
            Vector2f debug1Pos = StringAlignUtils.textBLToTarget(debug1,font,new Vector2f(0, Display.getHeight()));
            canvas1.drawString(debug1,debug1Pos.x,debug1Pos.y,font,strPaint);
            canvas1.drawString(debug2,debug1Pos.x,debug1Pos.y-spacing,font,strPaint);
            canvas1.drawString(debug3,debug1Pos.x,debug1Pos.y-(spacing*2),font,strPaint);
            strPaint.close(); font.close();
        });
    }
}
