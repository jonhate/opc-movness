package io.movness.computer;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/** The same URI parser and QR decoder shipped in the Android DEX, without an Android emulator. */
public class NativeFixture {
    public static void main(String[] args) throws Exception {
        String secret = "A".repeat(43);
        String link = "http://192.168.1.7:29871/pair#pair=" + secret;
        PairingUri parsed = PairingUri.parse(link);
        check(parsed.origin.equals("http://192.168.1.7:29871"), "origin");
        check(parsed.token.equals(secret), "token");
        for (String bad : new String[] {
            "https://example.com/pair#token=" + secret,
            "file:///pair#pair=" + secret,
            "https://user@example.com/pair#pair=" + secret,
            "https://example.com/pair?key=leak#pair=" + secret,
            "https://example.com/other#pair=" + secret,
            "https://example.com/pair#pair=short",
            "https://example.com/pair#pair=" + secret + "&extra=1"
        }) {
            try { PairingUri.parse(bad); throw new AssertionError("invalid QR accepted"); }
            catch (IllegalArgumentException error) { check(!error.getMessage().contains(secret), "error excludes token"); }
        }
        BitMatrix bits = new MultiFormatWriter().encode(link, BarcodeFormat.QR_CODE, 700, 700);
        BufferedImage image = new BufferedImage(700,700,BufferedImage.TYPE_INT_RGB);
        for (int y=0;y<700;y++) for(int x=0;x<700;x++) image.setRGB(x,y,bits.get(x,y)?0xff000000:0xffffffff);
        ByteArrayOutputStream jpeg=new ByteArrayOutputStream(); ImageIO.write(image,"jpg",jpeg);
        BufferedImage photo=ImageIO.read(new ByteArrayInputStream(jpeg.toByteArray()));
        String decoded=QrDecoder.decode(700,700,photo.getRGB(0,0,700,700,null,0,700));
        check(decoded.equals(link),"JPEG camera-format QR decoding");
        check(PairingUri.parse(decoded).token.equals(secret),"decode to production URI parser");
        System.out.println("PASS native URI validation, secret masking, JPEG QR decoding (11 assertions)");
    }
    private static void check(boolean ok,String detail) { if (!ok) throw new AssertionError(detail); }
}
