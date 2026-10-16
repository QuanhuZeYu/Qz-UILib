package club.heiqi.uilib.ui.media;

public final class MediaFrameKey {
    public final long sessionId, generation, sequence;
    public MediaFrameKey(long sessionId, long generation, long sequence) {
        this.sessionId=sessionId; this.generation=generation; this.sequence=sequence;
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof MediaFrameKey)) return false;
        MediaFrameKey k=(MediaFrameKey)other;
        return sessionId==k.sessionId && generation==k.generation && sequence==k.sequence;
    }
    @Override public int hashCode() {
        long h=(sessionId*31+generation)*31+sequence; return (int)(h^(h>>>32));
    }
    @Override public String toString() { return sessionId+":"+generation+":"+sequence; }
}
