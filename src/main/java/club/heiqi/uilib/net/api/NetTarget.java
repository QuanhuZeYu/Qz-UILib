package club.heiqi.uilib.net.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 出站消息目标。
 */
public final class NetTarget {

    private final Type type;
    private final Object player;
    private final List<Object> players;
    private final int dimensionId;

    private NetTarget(Type type, Object player, List<Object> players, int dimensionId) {
        this.type = type;
        this.player = player;
        this.players = players;
        this.dimensionId = dimensionId;
    }

    public static NetTarget server() {
        return new NetTarget(Type.SERVER, null, Collections.emptyList(), 0);
    }

    public static NetTarget player(Object player) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        return new NetTarget(Type.PLAYER, player, Collections.emptyList(), 0);
    }

    public static NetTarget players(Iterable<?> players) {
        List<Object> list = new ArrayList<Object>();
        for (Object player : players) {
            if (player != null) {
                list.add(player);
            }
        }
        return new NetTarget(Type.PLAYERS, null, Collections.unmodifiableList(list), 0);
    }

    public static NetTarget all() {
        return new NetTarget(Type.ALL, null, Collections.emptyList(), 0);
    }

    public static NetTarget dimension(int dimensionId) {
        return new NetTarget(Type.DIMENSION, null, Collections.emptyList(), dimensionId);
    }

    public Type getType() {
        return type;
    }

    public Object getPlayer() {
        return player;
    }

    public List<Object> getPlayers() {
        return players;
    }

    public int getDimensionId() {
        return dimensionId;
    }

    /**
     * 目标类型。
     */
    public enum Type {
        SERVER,
        PLAYER,
        PLAYERS,
        ALL,
        DIMENSION
    }
}
