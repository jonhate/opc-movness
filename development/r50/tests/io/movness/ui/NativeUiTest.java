package io.movness.ui;

import android.app.*;
import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=34,qualifiers="w393dp-h851dp-mdpi",shadows={NativeUiTest.Device.class,NativeUiTest.Storage.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeUiTest {
    ActivityController<Activity> controller;Activity activity;Fake backend;MovnessView view;int connections;
    @Implements(AccessibilityService.class)
    public static class Device extends ShadowAccessibilityService{
        static AccessibilityNodeInfo root;
        @Implementation protected AccessibilityNodeInfo getRootInActiveWindow(){return root;}
    }
    @Implements(Environment.class)
    public static class Storage extends ShadowEnvironment{
        @Implementation protected static boolean isExternalStorageManager(){return true;}
    }
    @Before public void setup(){
        controller=Robolectric.buildActivity(Activity.class);activity=controller.get();activity.setTheme(android.R.style.Theme_Material_Light_NoActionBar);controller.setup().visible();
        activity.getSharedPreferences("movness_frontend",0).edit().clear().putBoolean("quick_start_v1",true).commit();
        backend=new Fake();view=new MovnessView(activity,backend,()->connections++,()->{});activity.setContentView(view);
        shadowOf(Looper.getMainLooper()).idle();layout(view,393,851);
    }
    @After public void close(){controller.pause().stop().destroy();Device.root=null;}
    static void layout(View root,int w,int h){root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);}
    static TextView find(View root,String text){
        if(root instanceof TextView&&((TextView)root).getText().toString().equals(text))return (TextView)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextView result=find(((ViewGroup)root).getChildAt(i),text);if(result!=null)return result;}
        return null;
    }
    static String texts(View root){StringBuilder out=new StringBuilder();if(root instanceof TextView)out.append(((TextView)root).getText()).append('\n');if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)out.append(texts(((ViewGroup)root).getChildAt(i)));return out.toString();}
    static View description(View root,String text){if(Objects.equals(text,root.getContentDescription()==null?null:root.getContentDescription().toString()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=description(((ViewGroup)root).getChildAt(i),text);if(found!=null)return found;}return null;}
    static void click(View root,String label){View target=find(root,label);assertNotNull("Missing control: "+label,target);while(!target.hasOnClickListeners()&&target.getParent() instanceof View)target=(View)target.getParent();assertTrue("Missing click action: "+label,target.hasOnClickListeners());target.performClick();}
    Dialog dialog(){Dialog d=ShadowDialog.getLatestDialog();assertNotNull(d);return d;}
    void menu(){description(view,"打开菜单").performClick();shadowOf(Looper.getMainLooper()).idle();}
    static int bottomOn(View child,View root){int y=child.getBottom();while(child.getParent() instanceof View&&child.getParent()!=root){child=(View)child.getParent();y+=child.getTop();}return y;}
    static void nativeFont(View root){
        if(root instanceof TextView){TextView t=(TextView)root;t.setTypeface(Typeface.create(Typeface.createFromFile("r50-simple/tools/fonts/NotoSansCJKsc-Regular.otf"),t.getTypeface()==null?0:t.getTypeface().getStyle()));}
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)nativeFont(((ViewGroup)root).getChildAt(i));
    }
    static void capture(View root,String name,int width,int height)throws Exception{
        nativeFont(root);layout(root,width,height);Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(MovnessView.BG);root.draw(canvas);
        File out=new File("r50-simple/evidence/ui/"+name+".png");out.getParentFile().mkdirs();try(FileOutputStream stream=new FileOutputStream(out)){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();
    }
    @Test public void composerRemainsAtBottomAfterKeyboardResize()throws Exception{
        EditText input=view.findViewWithTag("movness.composer");input.setText("请帮我继续这份方案");layout(view,393,851);
        int before=851-bottomOn(input,view);assertTrue(before>=10&&before<40);
        capture(view,"chat",393,851);layout(view,393,500);int after=500-bottomOn(input,view);
        assertEquals(before,after);assertEquals(0,MovnessView.keyboardOverlap(500,500));assertEquals(350,MovnessView.keyboardOverlap(850,500));
        capture(view,"keyboard-viewport",393,500);
    }
    @Test public void directComputerEntryAndSingleFirstSend(){
        click(view,"连接 DSH");assertEquals(1,connections);
        backend.state.put("CurrentChatId",null);view.refresh();EditText input=view.findViewWithTag("movness.composer");input.setText("打开相机");view.findViewWithTag("movness.send").performClick();
        assertEquals(1,backend.created);assertEquals(1,backend.sent);assertEquals("打开相机",backend.lastSent);assertEquals("",input.getText().toString());
    }
    @Test public void settingsContainOnlyBasicModelAndPermissionControls()throws Exception{
        menu();click(dialog().getWindow().getDecorView(),"设置");shadowOf(Looper.getMainLooper()).idle();
        Dialog settings=dialog();String visible=texts(settings.getWindow().getDecorView());
        for(String unwanted:new String[]{"角色卡","世界书","请求头","模型参数","工作台"})assertFalse(unwanted,visible.contains(unwanted));
        click(settings.getWindow().getDecorView(),"模型");Dialog model=dialog();String text=texts(model.getWindow().getDecorView());
        assertTrue(text.contains("API 密钥"));assertTrue(text.contains("服务地址"));assertFalse(text.contains("请求头"));
        capture(model.getWindow().getDecorView(),"model",369,510);model.onBackPressed();assertFalse(model.isShowing());
        settings.onBackPressed();assertFalse(settings.isShowing());assertNotNull(view.findViewWithTag("movness.composer"));
    }
    @Test public void conversationsAreSeparateFromAutomaticTasksAndDraftSurvivesBack()throws Exception{
        EditText input=view.findViewWithTag("movness.composer");input.setText("尚未发送的内容");menu();click(dialog().getWindow().getDecorView(),"对话记录");
        Dialog history=dialog();assertTrue(texts(history.getWindow().getDecorView()).contains("昨晚的对话"));history.onBackPressed();assertEquals("尚未发送的内容",input.getText().toString());
        menu();click(dialog().getWindow().getDecorView(),"自动任务");Dialog tasks=dialog();assertFalse(texts(tasks.getWindow().getDecorView()).contains("昨晚的对话"));
        assertFalse(texts(tasks.getWindow().getDecorView()).contains("手动执行"));
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);while(!texts(tasks.getWindow().getDecorView()).contains("每天早上整理日程")&&System.nanoTime()<deadline){shadowOf(Looper.getMainLooper()).idle();Thread.yield();}
        assertTrue(texts(tasks.getWindow().getDecorView()).contains("每天早上整理日程"));tasks.onBackPressed();
        menu();capture(dialog().getWindow().getDecorView(),"menu",369,650);
    }
    @Test public void firstUseGuideIsVisibleAndOptionalStepsCanBeSkipped(){
        activity.getSharedPreferences("movness_frontend",0).edit().putBoolean("quick_start_v1",false).commit();view.refresh();shadowOf(Looper.getMainLooper()).idle();Dialog guide=dialog();String visible=texts(guide.getWindow().getDecorView());
        assertTrue(visible.contains("开始使用 Movness"));assertTrue(visible.contains("连接电脑（可选）"));click(guide.getWindow().getDecorView(),"进入 AI 对话");assertFalse(guide.isShowing());
        assertTrue(activity.getSharedPreferences("movness_frontend",0).getBoolean("quick_start_v1",false));
    }
    static AccessibilityNodeInfo node(String id,String text){
        AccessibilityNodeInfo n=AccessibilityNodeInfo.obtain();n.setPackageName("io.sample.target");n.setViewIdResourceName(id);n.setClassName("android.widget.Button");n.setText(text);n.setVisibleToUser(true);n.setEnabled(true);n.setClickable(true);return n;
    }
    @Test public void realAccessibilityEventsSaveAndReplayWithPerRunEvidence()throws Exception{
        ServiceController<LearningService> serviceController=Robolectric.buildService(LearningService.class).create();LearningService service=serviceController.get();service.onServiceConnected();
        ComponentName component=new ComponentName("io.sample.target","io.sample.target.MainActivity");ShadowPackageManager pm=shadowOf(service.getPackageManager());ActivityInfo info=pm.addActivityIfNotPresent(component);info.enabled=true;info.exported=true;
        IntentFilter launch=new IntentFilter(Intent.ACTION_MAIN);launch.addCategory(Intent.CATEGORY_LAUNCHER);pm.addIntentFilterForActivity(component,launch);
        AccessibilityNodeInfo target=node("io.sample.target:id/save","保存");Device.root=target;int[] clicks={0};shadowOf(target).setOnPerformActionListener((action,args)->{clicks[0]++;return true;});
        LearningStore.start(service);AccessibilityEvent event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_CLICKED);event.setPackageName("io.sample.target");shadowOf(event).setSourceNode(target);service.onAccessibilityEvent(event);
        JSONObject draft=LearningStore.finish(service);assertEquals(2,draft.getJSONArray("steps").length());String id=LearningStore.save(service,"打开收藏");
        JSONObject started=new JSONObject(LearningStore.run(service,id));String runId=started.getString("runId");assertEquals("running",started.getString("state"));shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2000));
        JSONObject result=new JSONObject(LearningStore.status(service,runId));assertEquals("completed",result.getString("state"));assertEquals(2,result.getInt("completedSteps"));assertEquals(1,clicks[0]);
        assertEquals("打开收藏",new JSONObject(LearningStore.list(service)).getJSONArray("skills").getJSONObject(0).getString("name"));serviceController.destroy();
    }
    @Test public void passwordsAndAmbiguousTargetsNeverAct()throws Exception{
        ServiceController<LearningService> controller=Robolectric.buildService(LearningService.class).create();LearningService service=controller.get();service.onServiceConnected();
        LearningStore.start(service);AccessibilityNodeInfo password=node("io.sample.target:id/password","secret");password.setPassword(true);AccessibilityEvent event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);event.setPackageName("io.sample.target");shadowOf(event).setSourceNode(password);service.onAccessibilityEvent(event);
        assertEquals(0,LearningStore.finish(service).getJSONArray("steps").length());
        AccessibilityNodeInfo root=node("io.sample.target:id/root","");AccessibilityNodeInfo a=node("io.sample.target:id/save","保存"),b=node("io.sample.target:id/save","保存");shadowOf(root).addChild(a);shadowOf(root).addChild(b);Device.root=root;
        assertFalse(LearningService.matches(new JSONObject().put("package","io.sample.target").put("viewId","io.sample.target:id/password"),password));
        try{PhoneActions.act(new JSONObject().put("action","click").put("package","io.sample.target").put("viewId","io.sample.target:id/save").toString());fail("Ambiguous target must stop");}catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("2"));}
        controller.destroy();
    }
    @Test public void markdownPreservesNumberedStepsAndEscapesHtml(){String rendered=ChatMarkdown.render("1. **打开**应用\n2. 保存结果\n\n<script>alert(1)</script>").toString();assertTrue(rendered.contains("1. 打开应用"));assertTrue(rendered.contains("2. 保存结果"));assertTrue(rendered.contains("<script>"));}
    @Test public void internalToolPayloadsStayOutOfConversation(){assertEquals("稍等",ConversationText.visible("稍等\n<tool name=\"phone_act\"><param>internal</param></tool>"));assertEquals("稍等",ConversationText.visible("稍等\n<tool name=\"phone_act\""));}
    public static final class Message{
        final String sender,content;final long time;Message(String sender,String content,long time){this.sender=sender;this.content=content;this.time=time;}
        public String getSender(){return sender;}public String getContent(){return content;}public long getTimestamp(){return time;}public Object getContentStream(){return null;}
    }
    public static final class History{public String getId(){return "old-chat";}public String getTitle(){return "昨晚的对话";}public long getUpdatedAt(){return 1;}}
    public static final class ConfigData{public String getId(){return "default";}}
    public static final class Fake implements Backend{
        final Map<String,Object> state=new HashMap<>();int created,sent;String lastSent;boolean automatic;
        Fake(){state.put("CurrentChatId","test-chat");state.put("CurrentChatIsLoading",false);state.put("IsConfigured",true);state.put("IsApiConfigInitialized",true);state.put("ErrorMessage",null);state.put("ApiKey","fixture-only");state.put("ApiEndpoint","https://example.invalid/v1/chat/completions");state.put("ModelName","测试模型");state.put("ApiProviderType","OPENAI_GENERIC");state.put("ActiveChatConfigId","default");state.put("ActiveChatModelConfig",new ConfigData());}
        public Object state(String key){return state.get(key);}
        public List<?> messages(){return Arrays.asList(new Message("user","帮我整理拍摄清单",1),new Message("ai","**拍摄清单已经整理。**\n\n1. 开场：校园清晨\n2. 中段：课堂与操场\n3. 结尾：傍晚的人物镜头",2));}
        public List<?> conversations(){return Collections.singletonList(new History());}
        public Object value(Object item,String getter){try{return item.getClass().getMethod(getter).invoke(item);}catch(Exception e){throw new IllegalStateException(e);}}
        public void newConversation(){created++;state.put("CurrentChatId","new-chat");}public void selectConversation(String id){state.put("CurrentChatId",id);}public void send(String text,String id){sent++;lastSent=text;}
        public void stop(String id){}public void collectStream(Object stream,StreamText callback){}public void close(){}public void conversationVisible(boolean visible){}
        public String incomingDraft(){return null;}public void updateDraft(String text){}public int attachmentCount(){return 0;}
        public boolean automaticMode(Context context){return automatic;}public void setAutomaticMode(Context context,boolean value){automatic=value;}
        public void saveConfiguration(Context c,String id,String key,String endpoint,String model,String protocol,boolean changed){state.put("ApiKey",key);state.put("ApiEndpoint",endpoint);state.put("ModelName",model);state.put("ApiProviderType",protocol);}
        public List<JSONObject> automaticTasks(Context context)throws Exception{return Collections.singletonList(new JSONObject().put("name","每天早上整理日程").put("enabled",true));}
    }
}
