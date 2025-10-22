package club.heiqi.qz_uilib.fontsystem.event;


import club.heiqi.qz_uilib.eventbus.api.Event;
import club.heiqi.qz_uilib.fontsystem.CharInfo;

import java.awt.image.BufferedImage;

public class GenerateDoneEvent extends Event {

    public int codepoint;
    public int type;
    public BufferedImage image;
    public CharInfo info;

    public GenerateDoneEvent(int codepoint, int type, BufferedImage image, CharInfo info) {
        this.codepoint = codepoint;
        this.type = type;
        this.image = image;
        this.info = info;
    }
}
