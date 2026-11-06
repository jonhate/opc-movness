package io.movness.ui;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Local, explicit demonstrations. Corrupt or incomplete recordings are never published. */
public final class LearningStore {
    private LearningStore(){}
    private static File root(Context c){File f=new File(c.getFilesDir(),"movness_learning");if(!f.isDirectory()&&!f.mkdirs())throw new IllegalStateException("无法创建学习记录目录");return f;}
    private static JSONObject read(File f)throws Exception{
        if(!f.exists())return new JSONObject();
        try(InputStream in=new AtomicFile(f).openRead()){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
            while((n=in.read(b))!=-1)out.write(b,0,n);
            return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
        }
    }
    private static void write(File f,JSONObject value)throws Exception{
        AtomicFile a=new AtomicFile(f);FileOutputStream stream=a.startWrite();
        try{stream.write(value.toString().getBytes(StandardCharsets.UTF_8));a.finishWrite(stream);}
        catch(Exception e){a.failWrite(stream);throw e;}
    }
    private static File session(Context c){return new File(root(c),"demonstration.json");}
    public static synchronized JSONObject sessionState(Context c)throws Exception{return read(session(c));}
    public static synchronized void start(Context c)throws Exception{
        if(sessionState(c).optBoolean("recording"))throw new IllegalStateException("已有示范正在录制");
        write(session(c),new JSONObject().put("recording",true).put("startedAt",System.currentTimeMillis()).put("steps",new JSONArray()).put("skipped",0));
    }
    public static synchronized void append(Context c,JSONObject step)throws Exception{
        JSONObject s=read(session(c));if(!s.optBoolean("recording"))return;
        JSONArray steps=s.getJSONArray("steps");
        if(steps.length()>=200)throw new IllegalStateException("本次示范已达200步，请结束保存");
        String pkg=step.getString("package");
        String previousPackage=steps.length()==0?"":steps.getJSONObject(steps.length()-1).getString("package");
        if(!pkg.equals(previousPackage))steps.put(new JSONObject().put("action","launch").put("package",pkg));
        if(step.getString("action").equals("text")&&steps.length()>0){
            JSONObject last=steps.getJSONObject(steps.length()-1);
            if(last.optString("action").equals("text")&&sameTarget(last,step))steps.put(steps.length()-1,step);
            else steps.put(step);
        }else steps.put(step);
        write(session(c),s);
    }
    static boolean sameTarget(JSONObject a,JSONObject b){
        for(String k:new String[]{"package","viewId","label","description","class"})if(!a.optString(k).equals(b.optString(k)))return false;
        return true;
    }
    public static synchronized void skip(Context c)throws Exception{JSONObject s=read(session(c));if(s.optBoolean("recording")){s.put("skipped",s.optInt("skipped")+1);write(session(c),s);}}
    public static synchronized JSONObject finish(Context c)throws Exception{JSONObject s=read(session(c));s.put("recording",false);write(session(c),s);return s;}
    public static synchronized void discard(Context c)throws Exception{write(session(c),new JSONObject().put("recording",false).put("steps",new JSONArray()));}
    public static synchronized String save(Context c,String name)throws Exception{
        if(name.trim().isEmpty())throw new IllegalArgumentException("请给这次学习起个名字");
        JSONObject s=read(session(c));if(s.optBoolean("recording"))throw new IllegalStateException("请先结束示范");
        if(s.getJSONArray("steps").length()<2)throw new IllegalStateException("没有捕获到可复用操作，请重新示范");
        String id=UUID.randomUUID().toString();s.put("id",id).put("name",name.trim()).put("savedAt",System.currentTimeMillis());
        write(new File(root(c),id+".json"),s);discard(c);return id;
    }
    private static File skillFile(Context c,String id){
        if(!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IllegalArgumentException("学习记录编号无效");
        return new File(root(c),id+".json");
    }
    public static synchronized JSONObject skill(Context c,String id)throws Exception{
        File f=skillFile(c,id);if(!f.isFile())throw new FileNotFoundException("找不到这条学习记录");return read(f);
    }
    public static synchronized String list(Context c)throws Exception{
        JSONArray list=new JSONArray();File[] files=root(c).listFiles();if(files==null)throw new IOException("无法读取学习记录");
        for(File f:files)if(f.getName().matches("[0-9a-f-]{36}\\.json")){
            JSONObject s=read(f);list.put(new JSONObject().put("id",s.getString("id")).put("name",s.getString("name"))
                .put("steps",s.getJSONArray("steps").length()).put("savedAt",s.getLong("savedAt")));
        }
        return new JSONObject().put("skills",list).toString();
    }
    public static synchronized void delete(Context c,String id)throws Exception{File f=skillFile(c,id);if(!f.delete())throw new IOException("删除学习记录失败");}
    public static synchronized String status(Context c)throws Exception{return read(new File(root(c),"replay.json")).toString();}
    public static synchronized String status(Context c,String runId)throws Exception{
        skillFile(c,runId);File f=new File(root(c),"replay-"+runId+".json");if(!f.isFile())throw new FileNotFoundException("找不到这次执行记录");return read(f).toString();
    }
    static synchronized void status(Context c,JSONObject value)throws Exception{
        write(new File(root(c),"replay.json"),value);
        if(value.has("runId")){String id=value.getString("runId");skillFile(c,id);write(new File(root(c),"replay-"+id+".json"),value);}
    }
    public static String run(Context c,String id)throws Exception{return LearningService.run(c,id);}
    public static String cancel(Context c)throws Exception{return LearningService.cancel(c);}
    public static String cancel(Context c,String runId)throws Exception{return LearningService.cancel(c,runId);}
}
