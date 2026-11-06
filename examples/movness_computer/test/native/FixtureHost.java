package io.movness.computer;
import java.io.File;
import java.nio.charset.StandardCharsets;
import okhttp3.MediaType;
import okhttp3.ResponseBody;

/** Test entrypoint for the exact production parser and result store, without Android wrappers. */
public final class FixtureHost {
    public static void main(String[] args) throws Exception {
        String result;
        if (args[0].equals("decode")) {
            result = RpcFileResponse.decode(ResponseBody.create(System.in.readAllBytes(), MediaType.get(args[1])));
        } else if (args[0].equals("save")) {
            result = ArtifactFiles.save(new File(args[1]), new String(System.in.readAllBytes(), StandardCharsets.UTF_8)).toString();
        } else if (args[0].equals("list")) {
            result = ArtifactFiles.list(new File(args[1])).toString();
        } else if (args[0].equals("preview")) {
            result = ArtifactFiles.preview(new File(args[1]), args[2]).toString();
        } else { throw new IllegalArgumentException("Unknown fixture operation"); }
        System.out.println(result);
    }
}
