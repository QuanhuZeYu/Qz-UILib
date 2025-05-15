package club.heiqi.qz_uilib.test;

public class Timer {

    public long startTime;
    public long endTime;

    public String timerName;

    public Timer(String name) {
        timerName = name;
    }

    public Timer start() {
        startTime = System.currentTimeMillis();
        return this;
    }

    public Timer end() {
        endTime = System.currentTimeMillis();
        return this;
    }

    public Timer reset() {
        startTime = 0; endTime = 0;
        return this;
    }

    public long getTotalMillis() {
        return endTime - startTime;
    }

    public double getTotalSecond() {
        return (double) getTotalMillis() / 1000;
    }
}
