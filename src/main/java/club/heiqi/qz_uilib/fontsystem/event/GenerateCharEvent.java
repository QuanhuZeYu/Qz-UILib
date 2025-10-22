package club.heiqi.qz_uilib.fontsystem.event;


import club.heiqi.qz_uilib.eventbus.api.Event;

public class GenerateCharEvent extends Event {

    public int codepoint;
    public int type;
    public int charSize;

    public GenerateCharEvent(int codepoint, int type, int charSize) {
        this.codepoint = codepoint;
        this.type = type;
        this.charSize = charSize;
    }
}
