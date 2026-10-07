package club.heiqi.uilib.host.client.font.vanilla;

import club.heiqi.uilib.ui.scene.text.TextEffects;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** wrap续行显式恢复全部活动flags；trim不伪造原source字符。 */
public final class StyleSerializer {
    private StyleSerializer(){ }
    public static String prefix(TextStyle style,VanillaTextService.Settings settings){
        StringBuilder result=new StringBuilder("§r");
        for(int i=0;i<16;i++)if((settings.paletteColor(i)&0xffffff)==(style.color&0xffffff)){result.append('§').append("0123456789abcdef".charAt(i));break;}
        TextEffects e=style.effects;if(e.obfuscated)result.append("§k");if(e.bold)result.append("§l");if(e.strike)result.append("§m");if(e.underline)result.append("§n");if(e.italic)result.append("§o");return result.toString();
    }
}
