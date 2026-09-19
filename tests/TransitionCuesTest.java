package com.jcm.whoopheartratepoc;

public final class TransitionCuesTest {
    private static int count;

    public static void main(String[] args) {
        check(TransitionCues.countdownNumber(3001, 3), 0);
        check(TransitionCues.countdownNumber(3000, 3), 3);
        check(TransitionCues.countdownNumber(2999, 3), 3);
        check(TransitionCues.countdownNumber(2000, 3), 2);
        check(TransitionCues.countdownNumber(1000, 3), 1);
        check(TransitionCues.countdownNumber(1, 3), 1);
        check(TransitionCues.countdownNumber(0, 3), 0);
        check(TransitionCues.countdownNumber(5000, 5), 5);
        check(TransitionCues.countdownNumber(4000, 5), 4);
        check(TransitionCues.countdownNumber(3000, 5), 3);
        check(TransitionCues.countdownNumber(2000, 5), 2);
        check(TransitionCues.countdownNumber(1000, 5), 1);
        check(TransitionCues.countdownNumber(1000, 0), 0);
        check(TransitionCues.previewThreshold(3), 10000);
        check(TransitionCues.previewThreshold(5), 12000);
        check(TransitionCues.previewThreshold(0), 0);
        System.out.println(count + " transition timing tests passed");
    }

    private static void check(long actual, long expected) {
        if (actual != expected) throw new AssertionError("Expected " + expected + ", got " + actual);
        count++;
    }
}
