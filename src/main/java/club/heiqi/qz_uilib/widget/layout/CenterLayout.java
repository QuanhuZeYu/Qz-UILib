package club.heiqi.qz_uilib.widget.layout;

import club.heiqi.qz_uilib.widget.Widget;

public class CenterLayout extends  DefaultLayout {

    @Override
    public void applyLayout(Widget widget) {
        super.applyLayout(widget);

        for (Widget child : curWidget.children) {
            child.x = ((curWidget.x + curWidget.width) / 2) - (child.width / 2);
            child.y = ((curWidget.y + curWidget.height) / 2) - (child.height / 2);
        }
    }
}
