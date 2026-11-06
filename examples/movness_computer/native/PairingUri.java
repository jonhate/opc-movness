package io.movness.computer;

import java.net.URI;
import java.util.Locale;

/** Recognizes the pinned remote plugin's QR format, without exposing its token in errors. */
public final class PairingUri {
    public final String origin;
    public final String token;
    private PairingUri(String origin, String token) { this.origin = origin; this.token = token; }
    public static PairingUri parse(String raw) {
        final URI uri;
        try { uri = new URI(raw.trim()); }
        catch (Exception error) { throw new IllegalArgumentException("无法识别配对二维码。"); }
        String scheme = uri.getScheme();
        if (scheme != null) scheme = scheme.toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null
                || uri.getRawUserInfo() != null || !"/pair".equals(uri.getRawPath())
                || uri.getRawQuery() != null || uri.getRawFragment() == null
                || !uri.getRawFragment().matches("pair=[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("请识别 DSH 远程控制插件生成的配对二维码。");
        }
        return new PairingUri(scheme + "://" + uri.getRawAuthority(), uri.getRawFragment().substring(5));
    }
}
