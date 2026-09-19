package com.jcm.whoopheartratepoc;

/** Pure timing helpers for spoken stage transitions. */
final class TransitionCues {
    private TransitionCues() {}

    static int countdownNumber(long remainingMillis, int countdownSeconds) {
        if (remainingMillis <= 0 || countdownSeconds <= 0) return 0;
        long seconds = (remainingMillis + 999) / 1000;
        return seconds <= countdownSeconds ? (int) seconds : 0;
    }

    static long previewThreshold(int countdownSeconds) {
        return countdownSeconds <= 0 ? 0 : (countdownSeconds + 7L) * 1000L;
    }
}
