package io.movness.computer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Private local results, published only after a complete and verified file write. */
public final class ArtifactFiles {
    private ArtifactFiles() {}
    public static JSONObject save(File root, String input) throws Exception {
        JSONObject source = new JSONObject(input);
        byte[] data = Base64.getDecoder().decode(source.getString("dataBase64"));
        if (data.length > RpcFileResponse.MAX_BYTES) throw new IOException("File exceeds the byte limit");
        if (!source.isNull("expectedBytes") && source.getLong("expectedBytes") != data.length) throw new IOException("File size mismatch");
        String hash = sha256(data);
        String id = sha256((source.getString("sessionId") + "\0" + source.getString("sourcePath") + "\0" + hash).getBytes(StandardCharsets.UTF_8));
        String name = source.getString("name").replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        while (name.startsWith(".")) name = name.substring(1);
        if (name.length() > 100) name = name.substring(name.length() - 100);
        if (name.isEmpty()) name = "成果";
        File directory = new File(root, id);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Result directory unavailable");
        File file = new File(directory, name);
        if (file.exists()) {
            if (!sha256(Files.readAllBytes(file.toPath())).equals(hash)) throw new IOException("Existing result is damaged");
        } else { atomicWrite(file, data); }
        JSONObject result = new JSONObject().put("id", id).put("name", name).put("bytes", data.length)
                .put("sha256", hash).put("phonePath", file.getAbsolutePath()).put("mime", mime(name))
                .put("sessionId", source.getString("sessionId")).put("sourcePath", source.getString("sourcePath"))
                .put("sourceVersion", source.getString("sourceVersion")).put("receivedAt", source.getLong("receivedAt"));
        atomicWrite(new File(root, id + ".json"), result.toString().getBytes(StandardCharsets.UTF_8));
        return result;
    }
    public static JSONArray list(File root) throws Exception {
        List<JSONObject> rows = new ArrayList<>();
        File[] files = root.listFiles();
        if (files == null) {
            if (root.exists()) throw new IOException("Result directory unreadable");
            return new JSONArray();
        }
        for (File file : files) {
            if (file.getName().matches("[a-f0-9]{64}\\.json")) {
                rows.add(new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)));
            }
        }
        rows.sort(Comparator.comparingLong((JSONObject row) -> row.optLong("receivedAt")).reversed());
        JSONArray result = new JSONArray();
        for (JSONObject row : rows) result.put(row);
        return result;
    }
    public static JSONObject verified(File root, String id) throws Exception {
        if (!id.matches("[a-f0-9]{64}")) throw new IOException("Invalid result identifier");
        JSONObject result = new JSONObject(new String(Files.readAllBytes(new File(root, id + ".json").toPath()), StandardCharsets.UTF_8));
        File file = new File(result.getString("phonePath"));
        if (!file.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)
                || file.length() != result.getLong("bytes") || file.length() > RpcFileResponse.MAX_BYTES
                || !id.equals(result.getString("id")) || !sha256(Files.readAllBytes(file.toPath())).equals(result.getString("sha256"))) throw new IOException("Result missing or damaged");
        return result;
    }
    public static JSONObject preview(File root, String id) throws Exception {
        JSONObject result = verified(root, id);
        String type = result.getString("mime");
        if (!(type.startsWith("text/") || type.equals("application/json"))) throw new IOException("Use an external viewer for this file");
        StringBuilder text = new StringBuilder();
        boolean truncated;
        try (Reader reader = Files.newBufferedReader(new File(result.getString("phonePath")).toPath(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[2048];
            while (text.length() < 24_000) {
                int count = reader.read(buffer, 0, Math.min(buffer.length, 24_000 - text.length()));
                if (count == -1) break;
                text.append(buffer, 0, count);
            }
            truncated = reader.read() != -1;
        }
        return new JSONObject().put("file", result).put("text", text.toString()).put("truncated", truncated);
    }
    private static void atomicWrite(File target, byte[] bytes) throws Exception {
        File temporary = File.createTempFile("movness-", ".part", target.getParentFile());
        try {
            try (FileOutputStream stream = new FileOutputStream(temporary)) { stream.write(bytes); stream.getFD().sync(); }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { if (temporary.exists() && !temporary.delete()) throw new IOException("Temporary result could not be removed"); }
    }
    public static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder value = new StringBuilder();
        for (byte part : digest) value.append(String.format(Locale.ROOT, "%02x", part & 0xff));
        return value.toString();
    }
    private static String mime(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".txt") || lower.endsWith(".log")) return "text/plain";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return "application/octet-stream";
    }
}
