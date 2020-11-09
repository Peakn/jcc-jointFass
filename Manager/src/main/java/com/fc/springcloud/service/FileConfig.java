package com.fc.springcloud.service;

import java.io.File;

public class FileConfig {
    private static final String PATH= "/Users/chenpeng/Desktop/demo/code/t";
    public static String MakeDirectory(){
        File file = null;
        file = new File(PATH);
        file.mkdirs();
        return file.getPath();
    }

    public static void main(String[] args) {
        long totalMilliSeconds = System.currentTimeMillis();
        System.out.println(totalMilliSeconds);
        String str = String.valueOf(totalMilliSeconds);
        System.out.println(str);
        String result = MakeDirectory();
        System.out.println();
        System.out.println(result);
    }
}
