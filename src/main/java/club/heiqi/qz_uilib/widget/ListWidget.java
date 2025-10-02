package club.heiqi.qz_uilib.widget;

import club.heiqi.qz_uilib.widget.layout.VerticalLayout;

public class ListWidget extends Widget {

    public int offsetX, offsetY;
    public ListWidget() {
        super();
        // 设置为垂直布局
        this.layout = new VerticalLayout();
    }

    @Override
    public void draw() {
        layout.applyLayout(this);

        // 所有内容都在裁切测试中绘制
        startScissor();

        for (Widget child : children) {
            child.draw();
        }

        endScissor();
    }

    @Override
    public void onWheel(float x, float y, int dWheel) {
        super.onWheel(x, y, dWheel);
        if (dWheel < 0) {
            offsetY += dWheel * 20;
        }
        else if (dWheel > 0) {
            offsetY += dWheel * 20;
        }
    }

    /**使用自身区域进行裁切*/
    @Override
    public void startScissor() {
        super.startScissor();
    }

    @Override
    public void endScissor() {
        super.endScissor();
    }
}
