package com.example.th7.data;

/** Converts SCREEN_OFF_TIMEOUT milliseconds into display-ready minute/second parts. */
public final class ScreenTimeoutFormatter {
    private ScreenTimeoutFormatter() { }

    public static Parts format(long timeoutMillis) {
        if (timeoutMillis <= 0) return null;
        long totalSeconds = timeoutMillis / 1000L;
        if (timeoutMillis < 1000L) totalSeconds = 1L;
        return new Parts(totalSeconds / 60L, (int) (totalSeconds % 60L));
    }

    public static final class Parts {
        public final long minutes;
        public final int seconds;

        private Parts(long minutes, int seconds) {
            this.minutes = minutes;
            this.seconds = seconds;
        }
    }
}
