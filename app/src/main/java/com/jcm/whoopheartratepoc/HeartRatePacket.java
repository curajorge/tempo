package com.jcm.whoopheartratepoc;

final class HeartRatePacket {
    static int parse(byte[] bytes) {
        if (bytes == null || bytes.length < 2) return -1;
        if ((bytes[0] & 1) == 0) return bytes[1] & 255;
        if (bytes.length < 3) return -1;
        return (bytes[1] & 255) | ((bytes[2] & 255) << 8);
    }
}
