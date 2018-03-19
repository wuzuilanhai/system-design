package com.biubiu.ip;

import java.util.Random;

/**
 * @author 张海彪
 * @create 2018-03-18 下午3:10
 */
public class IpFilter {

    private static final int BOUND = 256;

    private static char[] test = new char[512 * 1024 * 1024];

    public static void main(String[] args) {
        Random random = new Random();
        int a = random.nextInt(BOUND);
        int b = random.nextInt(BOUND);
        int c = random.nextInt(BOUND);
        int d = random.nextInt(BOUND);
        int ip = a * b * c * d;
        checkBlackList(ip);
    }

    //原理：IP从0.0.0.0到255.255.255.255，总共2^32个IP地址。每个IP地址只有两个状态：在黑名单，或者不在
    //因此最初设计是申请0x00000000到0xFFFFFFFF个字节的内存空间（4GB），建立全IP地址到内存的映射，每个字节存放一个二进位，存储该地址对应IP是不是黑名单IP
    //经过压缩之后，每个字节存放8个二进制位，因此总空间可压缩到原先的八分之一，即512MB
    //查询时，将IP地址的四个字节合组成一个int32并右移3位，得到该IP对应的字节，然后用这个int32的低三位确定字节里的二进制位，即是否是黑名单IP
    //将IP转换成int32之后，单次查询仅需要1次内存直接访问和3次位操作
    //适用于IP黑名单很大的情况
    private static int checkBlackList(int ip) {
        return test[ip >> 3] & (1 << (ip & 7));
    }

    private static void setIp(int ip, boolean inputValue) {
        int byteIndex = ip >> 3;
        char maskByte = (char) (1 << (ip & 7));
        test[byteIndex] = Character.highSurrogate(inputValue ? test[byteIndex] | maskByte : test[byteIndex] & maskByte);
    }

}
