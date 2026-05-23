package club.heiqi.uilib.net.api;

/**
 * 远端处理失败。
 */
public class NetRemoteException extends RuntimeException {

    public NetRemoteException(String message) {
        super(message);
    }
}
