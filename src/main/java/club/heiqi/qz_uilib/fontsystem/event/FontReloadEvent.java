package club.heiqi.qz_uilib.fontsystem.event;

import club.heiqi.qz_uilib.eventbus.api.Event;

public class FontReloadEvent extends Event {
    public float fontSize;

    public FontReloadEvent(float fontSize) {
        this.fontSize = fontSize;
    }
}
