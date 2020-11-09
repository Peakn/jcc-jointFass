package Manager;

import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;

public class FileConfig {
    static String source = "/Users/chenpeng/Desktop/counter.py";
    static String dst = "/Users/chenpeng/Desktop/demo/code";

    public static String fun(String a, String b, String c){
        c = a + b;
        return c;
    }
    public static void main(String[] args) throws IOException {
        String a = "a";
        String b = "b";
        String c = "c";

        System.out.println(fun(a, b, c));
    }
}
