package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.Collections;

/** 只维护文件选择特有的条目与面板定位，共同视觉来自 SceneAppearance。 */
final class FilePickerStyle extends SceneAppearance {
    FilePickerStyle(ScenePageContext page,ReadableSignal<SceneTheme> theme){super(page,theme);}
    SceneNode entry(ReadableSignal<FilePickerDirectory.Entry> entry,ReadableSignal<Boolean> selected,Runnable activate) {
        SceneNode button=SceneButton.createWithIcon(page,()->entry.get().name(),()->colors.get().theme(1,colors.get().text,10),
                activate,SceneTextPolicy.ELLIPSIS_SINGLE_LINE,()->entry.get().directory?SceneIconButton.Symbol.FOLDER:SceneIconButton.Symbol.FILE)
                .size(Size.fill(1),Size.natural());
        return column(0).control(new Surface(colors,Role.ENTRY,button,selected)).add(button);
    }
    SceneNode centered(SceneNode panel) {
        panel.bind(NodeBinding.value(colors,(n,p)->n.size(Size.fill(1).bounds(0,Math.round(p.source.textStyle.logicalEm*58)),
                Size.fill(1).bounds(0,Math.round(p.source.textStyle.logicalEm*46)))));
        return column(0).size(Size.fill(1),Size.fill(1)).control(new Center()).add(panel);
    }
    private static final class Center extends Flow {
        public ChildPlacements arrange(SceneNode n,LayoutBox box,SceneChildMeasurer children){
            SceneNode child=children.flowChildren().get(0);
            int w=child.width().constrain(Math.max(0,box.width-n.padding()*2));
            int h=child.height().constrain(Math.max(0,box.height-n.padding()*2));
            children.measureChild(child,new Constraints(w,h));
            return new ChildPlacements(Collections.singletonList(new ChildPlacements.Entry(child,
                    new LayoutBox((box.width-w)/2,(box.height-h)/2,w,h))));
        }
    }
}
