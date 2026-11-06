package io.movness.computer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.io.IOException;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;

/** Platform glue only. QR decoding, credential encryption and HTTP use existing libraries. */
public final class NativeBridge {
    private static final String STORE = "movness_computer_pairing";
    private static final String STATE = "state";
    private NativeBridge() {}

    public static String parsePairCode(String raw) throws Exception {
        PairingUri uri = PairingUri.parse(raw);
        return new JSONObject().put("origin", uri.origin).put("token", uri.token).toString();
    }

    public static String decodeQr(String path) throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("无法读取二维码图片。");
        int sample = 1;
        while (((long) options.outWidth * options.outHeight) / ((long) sample * sample) > 2_000_000L) sample *= 2;
        options.inJustDecodeBounds = false;
        options.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        if (bitmap == null) throw new IOException("无法读取二维码图片。");
        try {
            int width = bitmap.getWidth(), height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            return QrDecoder.decode(width, height, pixels);
        } finally { bitmap.recycle(); }
    }

    private static SharedPreferences preferences(Context context) throws Exception {
        MasterKey key = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
        return EncryptedSharedPreferences.create(context, STORE, key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }

    public static String readState(Context context) throws Exception {
        return preferences(context).getString(STATE, "{\"kind\":\"empty\"}");
    }

    public static void writeState(Context context, String state) throws Exception {
        if (!preferences(context).edit().putString(STATE, state).commit()) throw new IOException("电脑连接信息未能保存。");
    }

    /** Explicit local reset; preserves every other app preference and the shared Keystore key. */
    public static void resetState(Context context) throws Exception {
        if (!context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().clear().commit()) {
            throw new IOException("电脑连接信息未能移除。");
        }
    }

    private static File resultRoot(Context context) { return new File(context.getFilesDir(), "movness_computer_results"); }
    public static String saveArtifact(Context context, String input) throws Exception { return ArtifactFiles.save(resultRoot(context), input).toString(); }
    public static String listArtifacts(Context context) throws Exception { return ArtifactFiles.list(resultRoot(context)).toString(); }
    public static String artifactInfo(Context context, String id) throws Exception { return ArtifactFiles.verified(resultRoot(context), id).toString(); }
    public static String previewArtifact(Context context, String id) throws Exception { return ArtifactFiles.preview(resultRoot(context), id).toString(); }

    public interface HttpResult {
        void response(String result);
        void failure(String code);
    }

    /** Async callbacks keep the QuickJS thread available for stop and UI actions. */
    public static void request(String input, final HttpResult result) throws Exception {
        JSONObject value = new JSONObject(input);
        long timeout = value.getLong("timeoutMs");
        if (timeout < 1 || timeout > 60_000) throw new IllegalArgumentException("Invalid request timeout");
        OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false).connectTimeout(Math.min(timeout, 10_000), TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS).callTimeout(timeout, TimeUnit.MILLISECONDS).build();
        Request.Builder request = new Request.Builder().url(value.getString("url"));
        JSONObject headers = value.getJSONObject("headers");
        for (java.util.Iterator<String> names = headers.keys(); names.hasNext();) {
            String name = names.next(); request.header(name, headers.getString(name));
        }
        if ("POST".equals(value.getString("method"))) {
            request.post(RequestBody.create(value.getString("body"), MediaType.get("application/json; charset=utf-8")));
        } else if (!"GET".equals(value.getString("method"))) {
            throw new IllegalArgumentException("Unsupported HTTP method");
        }
        client.newCall(request.build()).enqueue(new Callback() {
            public void onFailure(Call call, IOException error) {
                try { result.failure("network_error"); }
                finally { client.dispatcher().executorService().shutdown(); client.connectionPool().evictAll(); }
            }
            public void onResponse(Call call, Response response) {
                try (Response closed = response) {
                    JSONObject outputHeaders = new JSONObject();
                    for (Map.Entry<String, List<String>> entry : response.headers().toMultimap().entrySet()) {
                        outputHeaders.put(entry.getKey(), String.join("\n", entry.getValue()));
                    }
                    if (response.body() == null) throw new IOException("Response body missing");
                    String media = response.body().contentType() == null ? "" : response.body().contentType().type() + "/" + response.body().contentType().subtype();
                    String body;
                    if ("multipart/form-data".equalsIgnoreCase(media)) {
                        if (!value.optBoolean("binary", false)) throw new IOException("Unexpected multipart response");
                        body = RpcFileResponse.decode(response.body());
                    } else { body = response.body().string(); }
                    result.response(new JSONObject().put("status", response.code())
                            .put("headers", outputHeaders).put("body", body).toString());
                } catch (Exception error) { result.failure("response_error"); }
                finally { client.dispatcher().executorService().shutdown(); client.connectionPool().evictAll(); }
            }
        });
    }
}
