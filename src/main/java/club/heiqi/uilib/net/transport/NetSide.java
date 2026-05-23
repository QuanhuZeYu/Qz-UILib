package club.heiqi.uilib.net.transport;

/**
 * 网络帧的接收侧。
 */
public enum NetSide {
    CLIENT(1),
    SERVER(2);

    private final int wireId;

    NetSide(int wireId) {
        this.wireId = wireId;
    }

    /**
     * 返回线协议 id。
     *
     * @return id
     */
    public int getWireId() {
        return wireId;
    }

    /**
     * 从线协议 id 解析接收侧。
     *
     * @param wireId id
     * @return 网络侧
     */
    public static NetSide fromWireId(int wireId) {
        for (NetSide side : values()) {
            if (side.wireId == wireId) {
                return side;
            }
        }
        throw new IllegalArgumentException("未知网络侧 id：" + wireId);
    }
}
