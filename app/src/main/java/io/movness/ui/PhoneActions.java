package io.movness.ui;

import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.accessibilityservice.AccessibilityService;
import org.json.*;
import java.util.*;

/** Phone tools share the same Android service and exact target resolver as demonstrations. */
public final class PhoneActions {
    private PhoneActions(){}
    public static String apps(Context context)throws Exception{
        JSONArray list=new JSONArray();Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Set<String> seen=new HashSet<>();for(ResolveInfo info:context.getPackageManager().queryIntentActivities(intent,0)){
            if(seen.add(info.activityInfo.packageName))list.put(new JSONObject().put("package",info.activityInfo.packageName).put("name",info.loadLabel(context.getPackageManager()).toString()));
        }
        return new JSONObject().put("apps",list).toString();
    }
    public static String launch(Context context,String pkg)throws Exception{
        Intent intent=context.getPackageManager().getLaunchIntentForPackage(pkg);if(intent==null)throw new IllegalArgumentException("该应用无法打开，请从应用列表核对包名");
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return new JSONObject().put("state","launch_requested").put("package",pkg).toString();
    }
    public static String screen()throws Exception{
        AccessibilityNodeInfo root=LearningService.require().getRootInActiveWindow();
        if(root==null)throw new IllegalStateException("当前没有可读取的界面");
        JSONArray nodes=new JSONArray();ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();queue.add(root);int visited=0;
        while(!queue.isEmpty()&&visited++<10000&&nodes.length()<160){
            AccessibilityNodeInfo n=queue.remove();
            if(n.isVisibleToUser()&&!LearningService.sensitive(n)&&(n.isClickable()||n.isEditable()||n.isScrollable()||n.getText()!=null||n.getContentDescription()!=null)){
                String label=LearningService.string(n.getText()),desc=LearningService.string(n.getContentDescription());
                JSONObject item=new JSONObject().put("package",LearningService.string(n.getPackageName())).put("viewId",LearningService.string(n.getViewIdResourceName()))
                    .put("label",label.length()>500?label.substring(0,500):label).put("description",desc.length()>500?desc.substring(0,500):desc)
                    .put("class",LearningService.string(n.getClassName())).put("clickable",n.isClickable()).put("editable",n.isEditable()).put("scrollable",n.isScrollable());
                nodes.put(item);
            }
            for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo child=n.getChild(i);if(child!=null)queue.add(child);}
        }
        return new JSONObject().put("package",LearningService.string(root.getPackageName())).put("nodes",nodes).put("truncated",!queue.isEmpty()).toString();
    }
    public static String act(String json)throws Exception{
        JSONObject step=new JSONObject(json);LearningService service=LearningService.require();String action=step.getString("action");boolean ok;
        if(action.equals("back")||action.equals("home"))ok=service.performGlobalAction(action.equals("back")?AccessibilityService.GLOBAL_ACTION_BACK:AccessibilityService.GLOBAL_ACTION_HOME);
        else{
            if(!(action.equals("click")||action.equals("longClick")||action.equals("text")||action.equals("scrollForward")||action.equals("scrollBackward")))throw new IllegalArgumentException("不支持的操作类型");
            if(step.optString("package").isEmpty()||(step.optString("viewId").isEmpty()&&step.optString("label").isEmpty()&&step.optString("description").isEmpty()))throw new IllegalArgumentException("请提供界面中实际存在的唯一控件标识");
            AccessibilityNodeInfo root=service.getRootInActiveWindow();if(root==null)throw new IllegalStateException("当前没有可操作的界面");
            List<AccessibilityNodeInfo> found=service.targets(root,step);if(found.size()!=1)throw new IllegalStateException("目标控件数量为 "+found.size()+"，操作未执行");
            AccessibilityNodeInfo target=found.get(0);
            if(action.equals("text")){Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,step.getString("text"));ok=target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args);}
            else ok=target.performAction(action.equals("click")?AccessibilityNodeInfo.ACTION_CLICK:action.equals("longClick")?AccessibilityNodeInfo.ACTION_LONG_CLICK:action.equals("scrollForward")?AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        }
        if(!ok)throw new IllegalStateException("应用未接受本次操作");
        return new JSONObject().put("state","action_accepted").put("action",action).put("verify","读取当前界面核对结果").toString();
    }
}
