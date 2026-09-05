package com.jcm.whoopheartratepoc;

public final class HeartRatePacketTest {
    public static void main(String[] args) {
        check(null, -1);
        check(new byte[0], -1);
        check(new byte[]{0}, -1);
        check(new byte[]{0, 72}, 72);
        check(new byte[]{0, (byte) 180}, 180);
        check(new byte[]{1, 0, 1}, 256);
        check(new byte[]{1, 72}, -1);
        check(new byte[]{0x16, 98, 0, 0}, 98);
        System.out.println("8 BLE heart-rate packet tests passed");
    }
    static void check(byte[] bytes, int expected) {
        if (HeartRatePacket.parse(bytes) != expected) throw new AssertionError("Expected " + expected);
    }
}
