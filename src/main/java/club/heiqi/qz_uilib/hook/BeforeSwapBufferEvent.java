package club.heiqi.qz_uilib.hook;

import java.util.ArrayList;
import java.util.List;

public class BeforeSwapBufferEvent {
    public static List<Runnable> runnable = new ArrayList<>();

    public static void run() {
        for (Runnable r : runnable) {
            r.run();
        }
        runnable.clear();
    }
}
