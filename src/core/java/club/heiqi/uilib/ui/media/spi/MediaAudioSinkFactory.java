package club.heiqi.uilib.ui.media.spi;

import club.heiqi.uilib.ui.media.MediaBudget;

public interface MediaAudioSinkFactory {
    MediaAudioSink open(MediaBudget budget) throws Exception;
}
