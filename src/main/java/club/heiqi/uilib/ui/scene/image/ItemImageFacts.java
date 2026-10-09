package club.heiqi.uilib.ui.scene.image;

import java.util.Objects;

/** 宿主准备的只读覆盖层/提示事实；核心不读宿主物品或语言上下文。 */
public final class ItemImageFacts {
    public final String quantity, tooltip;
    public final boolean durabilityVisible;
    public final double durabilityRemaining;
    public final int durabilityColor;
    private final int[] tooltipPalette;
    public ItemImageFacts(String quantity, String tooltip, boolean durabilityVisible, double durabilityRemaining, int durabilityColor) {
        this(quantity,tooltip,durabilityVisible,durabilityRemaining,durabilityColor,null);
    }
    public ItemImageFacts(String quantity,String tooltip,boolean durabilityVisible,double durabilityRemaining,int durabilityColor,int[] tooltipPalette){
        if(tooltipPalette!=null&&tooltipPalette.length<16)throw new IllegalArgumentException("物品提示palette不足");
        this.tooltipPalette=tooltipPalette==null?null:tooltipPalette.clone();
        this.quantity = Objects.requireNonNull(quantity, "quantity"); this.tooltip = Objects.requireNonNull(tooltip, "tooltip");
        if (!Double.isFinite(durabilityRemaining) || durabilityRemaining < 0 || durabilityRemaining > 1) throw new IllegalArgumentException("无效耐久事实");
        this.durabilityVisible = durabilityVisible; this.durabilityRemaining = durabilityRemaining; this.durabilityColor = durabilityColor;
    }
    /** 原版提示的§经已有正式format入口转属性文字；按当前主题base style，不冻结字体或吞bold。 */
    public club.heiqi.uilib.ui.scene.text.AttributedText formattedTooltip(club.heiqi.uilib.ui.scene.text.TextStyle style){
        if(tooltipPalette==null)return null;
        return club.heiqi.uilib.ui.scene.text.format.MinecraftFormat.parse(tooltip,style,new club.heiqi.uilib.ui.scene.text.format.MinecraftFormat.Context(){
            public int paletteColor(int index){return tooltipPalette[index];}
            public club.heiqi.uilib.ui.scene.text.TextEffects effects(boolean b,boolean i,boolean u,boolean s,boolean k){return new club.heiqi.uilib.ui.scene.text.TextEffects(b,i,u,s,k,0,0);}
            public club.heiqi.uilib.ui.scene.text.TextDecoration decoration(){return style.decoration;}
        });
    }
}
