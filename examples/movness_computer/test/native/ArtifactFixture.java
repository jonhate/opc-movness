package io.movness.computer;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import org.json.JSONObject;

public final class ArtifactFixture {
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("movness-results-").toFile();
        byte[] content = "# 校园一小时\r\n\r\n保留结尾换行。\r\n".getBytes(StandardCharsets.UTF_8);
        JSONObject request = input(content).put("name", "../分镜.md");
        request.put("expectedBytes", content.length + 1);
        try { ArtifactFiles.save(root, request.toString()); throw new AssertionError("wrong size accepted"); }
        catch (IOException expected) { check(ArtifactFiles.list(root).length()==0,"incomplete result not published"); }
        request.put("expectedBytes",content.length);
        JSONObject result = ArtifactFiles.save(root, request.toString());
        check(result.getString("sha256").equals(ArtifactFiles.sha256(content)),"exact bytes hashed");
        check(new File(result.getString("phonePath")).getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator),"filename remains in private result root");
        check(java.util.Arrays.equals(Files.readAllBytes(new File(result.getString("phonePath")).toPath()),content),"CRLF and final newline preserved");
        check(ArtifactFiles.preview(root,result.getString("id")).getString("text").equals(new String(content,StandardCharsets.UTF_8)),"preview does not alter stored bytes");
        ArtifactFiles.save(root,request.toString());check(ArtifactFiles.list(root).length()==1,"same file is not duplicated");
        JSONObject other=ArtifactFiles.save(root,new JSONObject(request.toString()).put("sourcePath","/fixture/other.md").put("name","other.md").toString());
        check(!other.getString("id").equals(result.getString("id")),"different source files with identical bytes remain distinct");
        check(ArtifactFiles.list(root).length()==2,"identical content cannot hide another source file");
        JSONObject empty=ArtifactFiles.save(root,input(new byte[0]).put("name","empty.txt").toString());
        check(empty.getLong("bytes")==0,"empty file preserved");
        byte[] binary = new byte[] {0,-1,1,2,13,10,0};
        JSONObject image = ArtifactFiles.save(root,input(binary).put("name","image.png").toString());
        check(java.util.Arrays.equals(Files.readAllBytes(new File(image.getString("phonePath")).toPath()),binary),"binary is not decoded as text");
        Files.write(new File(result.getString("phonePath")).toPath(), "changed".getBytes(StandardCharsets.UTF_8));
        try { ArtifactFiles.verified(root,result.getString("id"));throw new AssertionError("damaged file accepted"); }
        catch(IOException expected) { }
        System.out.println("PASS result store: exact bytes, CRLF, size rejection, no partial publication, path handling, source-aware deduplication, empty/binary files and damage detection");
    }
    private static JSONObject input(byte[] bytes) throws Exception {
        return new JSONObject().put("name","result.md").put("dataBase64",Base64.getEncoder().encodeToString(bytes))
            .put("expectedBytes",bytes.length).put("sessionId","fixture-session").put("sourcePath","/fixture/result.md")
            .put("sourceVersion","fixture-version").put("receivedAt",1);
    }
    private static void check(boolean ok,String detail) {if(!ok)throw new AssertionError(detail);}
}
