package tw.com.jchang.geniiecgbt;

public class decompNDK {
    static {
        System.loadLibrary("myDecompJNI");
    }

    public native int decpEcgFile(String path);
}
