package io.movness.ui;

import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.os.*;
import android.view.accessibility.*;
import java.util.*;
import org.json.*;

/** Records Android accessibility events and replays exact, unique semantic targets. */
public final class LearningService extends AccessibilityService {
    private static volatile LearningService instance;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<String> homePackages=new HashSet<>();
    private volatile Replay replay;
    public static boolean connected(){return instance!=null;}
    static LearningService require(){LearningService s=instance;if(s==null)throw new IllegalStateException("请先开启 Movness 手机操作与学习权限");return s;}
    @Override public void onServiceConnected(){
        instance=this;
        Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for(ResolveInfo info:getPackageManager().queryIntentActivities(home,0))homePackages.add(info.activityInfo.packageName);
        try{
            JSONObject old=new JSONObject(LearningStore.status(this));
            if(old.optString("state").equals("running"))LearningStore.status(this,old.put("state","interrupted").put("message","服务已重启，操作未自动重做"));
        }catch(Exception e){android.util.Log.e("MovnessLearning","Replay state could not be read",e);}
    }
    @Override public void onDestroy(){if(replay!=null)replay.stop("interrupted","学习服务已停止");instance=null;super.onDestroy();}
    @Override public void onInterrupt(){if(replay!=null)replay.stop("interrupted","操作被系统中断");}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(replay!=null)return;
        CharSequence sourcePackage=event.getPackageName();if(sourcePackage==null)return;
        String pkg=sourcePackage.toString();
        if(pkg.equals(getPackageName())||homePackages.contains(pkg)||pkg.contains("inputmethod")||pkg.equals("com.android.systemui"))return;
        try{
            if(!LearningStore.sessionState(this).optBoolean("recording"))return;
            int type=event.getEventType();String action;
            if(type==AccessibilityEvent.TYPE_VIEW_CLICKED)action="click";
            else if(type==AccessibilityEvent.TYPE_VIEW_LONG_CLICKED)action="longClick";
            else if(type==AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)action="text";
            else if(type==AccessibilityEvent.TYPE_VIEW_SCROLLED&&Build.VERSION.SDK_INT>=28&&event.getScrollDeltaY()!=0)action=event.getScrollDeltaY()>0?"scrollForward":"scrollBackward";
            else return;
            AccessibilityNodeInfo node=event.getSource();
            if(node==null||event.isPassword()||sensitive(node)){LearningStore.skip(this);return;}
            String id=string(node.getViewIdResourceName()),label=string(node.getText()),desc=string(node.getContentDescription());
            if(action.equals("text"))label="";
            if(id.isEmpty()&&label.isEmpty()&&desc.isEmpty()){LearningStore.skip(this);return;}
            JSONObject step=new JSONObject().put("action",action).put("package",pkg).put("viewId",id)
                .put("label",label).put("description",desc).put("class",string(node.getClassName()));
            if(action.equals("text")){
                String text=string(node.getText());if(text.length()>3000){LearningStore.skip(this);return;}
                step.put("text",text);
            }
            LearningStore.append(this,step);
        }catch(Exception e){
            android.util.Log.e("MovnessLearning","Recording stopped after a storage error",e);
            try{LearningStore.finish(this);}catch(Exception second){android.util.Log.e("MovnessLearning","Could not finish recording",second);}
        }
    }
    static String string(CharSequence s){return s==null?"":s.toString();}
    static boolean sensitive(AccessibilityNodeInfo node){
        int depth=0;for(AccessibilityNodeInfo n=node;n!=null;n=n.getParent()){
            if(depth++>64)return true;
            int input=n.getInputType(),kind=input&android.text.InputType.TYPE_MASK_CLASS,variation=input&android.text.InputType.TYPE_MASK_VARIATION;
            if(n.isPassword()||(kind==android.text.InputType.TYPE_CLASS_TEXT&&(variation==android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD||variation==android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD||variation==android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))||(kind==android.text.InputType.TYPE_CLASS_NUMBER&&variation==android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD))return true;
        }
        return false;
    }
    public static synchronized String run(Context context,String id)throws Exception{
        LearningService service=instance;
        if(service==null)throw new IllegalStateException("请先开启 Movness 示范学习权限");
        if(service.replay!=null)throw new IllegalStateException("已有学习操作正在执行");
        if(LearningStore.sessionState(context).optBoolean("recording"))throw new IllegalStateException("请先结束正在录制的示范");
        JSONObject skill=LearningStore.skill(context,id);String runId=UUID.randomUUID().toString();
        Replay task=service.new Replay(runId,skill);service.replay=task;task.publish("running","已开始");service.main.post(task);
        return new JSONObject().put("state","running").put("runId",runId).put("name",skill.getString("name")).toString();
    }
    public static String cancel(Context context)throws Exception{
        LearningService service=instance;
        if(service!=null&&service.replay!=null){Replay current=service.replay;service.main.post(()->current.stop("cancelled","已停止"));}
        return LearningStore.status(context);
    }
    public static synchronized String cancel(Context context,String runId)throws Exception{
        LearningService service=require();Replay current=service.replay;
        if(current==null)return LearningStore.status(context,runId);
        if(!current.id.equals(runId))throw new IllegalStateException("当前执行编号不同，未取消其他任务");
        service.main.post(()->current.stop("cancelled","已停止"));
        return new JSONObject().put("state","cancellation_requested").put("runId",runId).toString();
    }
    /** Matching never guesses coordinates or picks the first of several candidates. */
    static boolean matches(JSONObject step,AccessibilityNodeInfo node){
        if(!step.optString("package").equals(string(node.getPackageName())))return false;
        for(String key:new String[]{"viewId","label","description","class"}){
            String wanted=step.optString(key);if(wanted.isEmpty())continue;
            String actual=key.equals("viewId")?string(node.getViewIdResourceName()):key.equals("label")?string(node.getText()):key.equals("description")?string(node.getContentDescription()):string(node.getClassName());
            if(!wanted.equals(actual))return false;
        }
        return !sensitive(node)&&node.isVisibleToUser();
    }
    List<AccessibilityNodeInfo> targets(AccessibilityNodeInfo root,JSONObject step){
        List<AccessibilityNodeInfo> found=new ArrayList<>();ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();queue.add(root);
        int visited=0;
        while(!queue.isEmpty()&&visited++<10000){AccessibilityNodeInfo n=queue.remove();if(matches(step,n))found.add(n);
            for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo child=n.getChild(i);if(child!=null)queue.add(child);}}
        if(!queue.isEmpty())throw new IllegalStateException("页面控件过多，目标尚未完整核对");
        return found;
    }
    private final class Replay implements Runnable{
        final String id;final JSONObject skill;final JSONArray steps;int index;long since=SystemClock.uptimeMillis();boolean stopped;
        Replay(String id,JSONObject skill)throws Exception{this.id=id;this.skill=skill;this.steps=skill.getJSONArray("steps");}
        void publish(String state,String message)throws Exception{LearningStore.status(LearningService.this,new JSONObject().put("runId",id).put("skillId",skill.getString("id"))
            .put("name",skill.getString("name")).put("state",state).put("completedSteps",index).put("totalSteps",steps.length()).put("message",message));}
        void stop(String state,String message){
            stopped=true;main.removeCallbacks(this);
            try{publish(state,message);}catch(Exception e){android.util.Log.e("MovnessLearning","Replay result could not be saved",e);}
            if(replay==this)replay=null;
        }
        public void run(){
            if(stopped)return;
            try{
                if(index==steps.length()){stop("completed","已完成全部记录步骤");return;}
                JSONObject step=steps.getJSONObject(index);String action=step.getString("action");
                if(action.equals("launch")){
                    Intent intent=getPackageManager().getLaunchIntentForPackage(step.getString("package"));
                    if(intent==null){stop("failed","第"+(index+1)+"步的应用无法打开");return;}
                    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));advance(900);return;
                }
                AccessibilityNodeInfo root=getRootInActiveWindow();
                List<AccessibilityNodeInfo> candidates=root==null?Collections.emptyList():targets(root,step);
                if(candidates.size()>1){stop("failed","第"+(index+1)+"步存在多个相同控件，请重新示范这一操作");return;}
                if(candidates.isEmpty()){
                    if(SystemClock.uptimeMillis()-since>8000){stop("failed","未找到第"+(index+1)+"步的控件，界面可能已变化");return;}
                    main.postDelayed(this,200);return;
                }
                AccessibilityNodeInfo target=candidates.get(0);boolean ok;
                if(action.equals("text")){
                    Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,step.getString("text"));
                    ok=target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args);
                }else{
                    int code=action.equals("click")?AccessibilityNodeInfo.ACTION_CLICK:action.equals("longClick")?AccessibilityNodeInfo.ACTION_LONG_CLICK:action.equals("scrollForward")?AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
                    ok=target.performAction(code);
                }
                if(!ok){stop("failed","第"+(index+1)+"步未被应用接受，操作已停止");return;}
                advance(450);
            }catch(Exception e){android.util.Log.e("MovnessLearning","Replay failed",e);stop("failed","执行失败："+e.getClass().getSimpleName());}
        }
        void advance(long delay)throws Exception{index++;since=SystemClock.uptimeMillis();publish("running","执行第"+index+"步 / "+steps.length());main.postDelayed(this,delay);}
    }
}
