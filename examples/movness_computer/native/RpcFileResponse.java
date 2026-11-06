package io.movness.computer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.MultipartReader;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import org.json.JSONArray;
import org.json.JSONObject;

/** DSH's published multipart RPC, parsed by the OkHttp already embedded in r46. */
public final class RpcFileResponse {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final Pattern FIELD = Pattern.compile("(?:^|;\\s*)name=\"([^\"]+)\"");
    private RpcFileResponse() {}
    public static String decode(ResponseBody body) throws Exception {
        Map<String, byte[]> parts = new HashMap<>();
        try (MultipartReader reader = new MultipartReader(body)) {
            MultipartReader.Part part;
            while ((part = reader.nextPart()) != null) {
                try {
                    String disposition = part.headers().get("Content-Disposition");
                    Matcher match = FIELD.matcher(disposition == null ? "" : disposition);
                    if (!match.find()) throw new IOException("File response field missing");
                    String name = match.group(1);
                    if (parts.containsKey(name) || parts.size() >= 2) throw new IOException("Unexpected file response fields");
                    parts.put(name, bounded(part.body(), "metadata".equals(name) ? 64 * 1024 : MAX_BYTES));
                } finally { part.close(); }
            }
        }
        byte[] metadata = parts.remove("metadata");
        if (metadata == null || parts.size() != 1) throw new IOException("File response incomplete");
        JSONObject envelope = new JSONObject(new String(metadata, StandardCharsets.UTF_8));
        if (!"server-response".equals(envelope.getString("type")) || !envelope.getJSONObject("result").getBoolean("ok")) {
            throw new IOException("Invalid file RPC response");
        }
        JSONArray attachments = envelope.getJSONArray("attachments");
        if (attachments.length() != 1) throw new IOException("Expected one file attachment");
        JSONObject attachment = attachments.getJSONObject(0);
        JSONArray path = attachment.getJSONArray("path");
        if (!"bytes".equals(attachment.getString("codec")) || path.length() != 1 || !"data".equals(path.getString(0))) {
            throw new IOException("Unexpected file attachment path");
        }
        byte[] bytes = parts.remove(attachment.getString("part"));
        if (bytes == null || !parts.isEmpty()) throw new IOException("File attachment missing");
        JSONObject value = envelope.getJSONObject("result").getJSONObject("value");
        if (!value.has("data") || !value.isNull("data")) throw new IOException("Invalid file attachment placeholder");
        value.remove("data");
        value.put("dataBase64", Base64.getEncoder().encodeToString(bytes));
        return envelope.toString();
    }
    private static byte[] bounded(BufferedSource source, int limit) throws IOException {
        Buffer bytes = new Buffer();
        while (true) {
            long count = source.read(bytes, Math.min(8192L, limit - bytes.size() + 1L));
            if (count == -1) return bytes.readByteArray();
            if (bytes.size() > limit) throw new IOException("File response exceeds the byte limit");
        }
    }
}
