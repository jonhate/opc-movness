package io.movness.computer;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

/** The same ZXing decoder is exercised by JVM fixtures and the Android camera adapter. */
public final class QrDecoder {
    private QrDecoder() {}
    public static String decode(int width, int height, int[] pixels) throws Exception {
        return new MultiFormatReader().decode(new BinaryBitmap(
                new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)))).getText();
    }
}
