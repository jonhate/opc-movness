package io.movness.ui;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.text.method.LinkMovementMethod;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Movness's original rounded native composer, backed by the existing agent engine. */
public final class MovnessView extends LinearLayout {
    static final int BG=0xfffafaff,INK=0xff252735,MUTED=0xff818697,BLUE=0xff5e63e6,BORDER=0xffe4e6ef,PALE=0xffeeefff;
    private final Backend backend;private final Runnable computer,permissions;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final android.content.SharedPreferences prefs;
    private final ScrollView scroll;private final LinearLayout messages;private final EditText input;private final ImageButton send;
    private final TextView status,learningBanner,attachmentBanner;private final Map<String,TextView> bubbles=new LinkedHashMap<>();
    private final Map<String,String> rendered=new HashMap<>();
    private final Set<Dialog> dialogs=new HashSet<>();
    private final Map<Object,String> streams=new IdentityHashMap<>();
    private boolean attached,submitting,bindingDraft,pendingLearning,pendingAutomations,guideShown,forceBottom;
    private String draftKey="draft:none",lastChat="",lastError="",localError="";private Dialog guide;
    private final ViewTreeObserver.OnGlobalLayoutListener geometry=this::layoutInsets;
    private final Runnable tick=new Runnable(){public void run(){if(!attached)return;refresh();main.postDelayed(this,250);}};
    public MovnessView(Context context,Backend backend,Runnable computer,Runnable permissions){
        super(context);this.backend=backend;this.computer=computer;this.permissions=permissions;
        prefs=context.getSharedPreferences("movness_frontend",Context.MODE_PRIVATE);
        setOrientation(VERTICAL);setBackgroundColor(BG);setFocusableInTouchMode(true);
        setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        setTag("movness.ai_chat");
        LinearLayout header=row();header.setPadding(dp(20),dp(10),dp(12),dp(10));
        TextView title=text("Movness",25,INK);title.setTypeface(null,Typeface.BOLD);header.addView(title,new LayoutParams(0,dp(52),1));
        TextView link=button("连接 DSH",this::connect);link.setTextSize(14);link.setTextColor(BLUE);link.setBackground(pill(0xffffffff,22,BORDER));
        header.addView(link,new LayoutParams(dp(104),dp(44)));
        ImageButton menu=icon("menu","打开菜单",this::menu);LayoutParams menuLp=new LayoutParams(dp(44),dp(44));menuLp.leftMargin=dp(8);header.addView(menu,menuLp);addView(header);
        learningBanner=text("",13,BLUE);learningBanner.setPadding(dp(20),dp(10),dp(20),dp(10));learningBanner.setBackgroundColor(PALE);learningBanner.setVisibility(GONE);learningBanner.setOnClickListener(v->finishDemonstration());addView(learningBanner);
        scroll=new ScrollView(context);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setPadding(dp(20),0,dp(20),0);scroll.setVerticalScrollBarEnabled(false);
        messages=column();messages.setPadding(0,dp(10),0,dp(16));scroll.addView(messages,new ScrollView.LayoutParams(-1,-2));addView(scroll,new LayoutParams(-1,0,1));
        status=text("",13,MUTED);status.setPadding(dp(22),dp(6),dp(22),dp(6));status.setVisibility(GONE);addView(status);
        LinearLayout composer=row();composer.setGravity(Gravity.BOTTOM);composer.setPadding(dp(17),dp(7),dp(8),dp(7));composer.setBackground(pill(0xffffffff,29,BORDER));composer.setElevation(dp(2));
        input=new EditText(context);input.setId(0x4d560050);input.setTag("movness.composer");input.setHint("想做什么？");input.setHintTextColor(MUTED);input.setTextColor(INK);input.setTextSize(18);
        input.setBackground(null);input.setPadding(dp(2),dp(10),dp(10),dp(10));input.setMinHeight(dp(48));input.setMaxLines(5);input.setGravity(Gravity.TOP|Gravity.START);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);composer.addView(input,new LayoutParams(0,-2,1));
        send=icon("send","发送",this::submit);send.setTag("movness.send");send.setBackground(pill(BLUE,25,BLUE));composer.addView(send,new LayoutParams(dp(48),dp(48)));
        LayoutParams clp=new LayoutParams(-1,-2);clp.setMargins(dp(16),dp(8),dp(16),dp(14));addView(composer,clp);
        attachmentBanner=text("",13,BLUE);attachmentBanner.setPadding(dp(22),dp(3),dp(22),dp(5));attachmentBanner.setVisibility(GONE);addView(attachmentBanner,indexOfChild(composer));
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){if(!bindingDraft){prefs.edit().putString(draftKey,s.toString()).apply();backend.updateDraft(s.toString());}}public void afterTextChanged(Editable s){updateSend();}});
        getViewTreeObserver().addOnGlobalLayoutListener(geometry);
    }
    int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout l=new LinearLayout(getContext());l.setOrientation(VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(getContext());l.setOrientation(HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    TextView text(String s,int size,int color){TextView t=new TextView(getContext());t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);t.setLineSpacing(dp(3),1);return t;}
    TextView button(String label,Runnable action){TextView t=text(label,16,INK);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(48));t.setPadding(dp(12),dp(10),dp(12),dp(10));t.setOnClickListener(v->action.run());return t;}
    TextView primary(String label,Runnable action){TextView t=button(label,action);t.setTextColor(0xffffffff);t.setBackground(pill(BLUE,22,BLUE));LayoutParams lp=new LayoutParams(-1,dp(52));lp.topMargin=dp(16);t.setLayoutParams(lp);return t;}
    GradientDrawable pill(int fill,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;}
    ImageButton icon(String type,String description,Runnable action){ImageButton b=new ImageButton(getContext());b.setImageDrawable(new LineIcon(type));b.setContentDescription(description);b.setBackgroundColor(Color.TRANSPARENT);b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setOnClickListener(v->action.run());return b;}
    Activity activity(){Context c=getContext();while(c instanceof ContextWrapper){if(c instanceof Activity)return (Activity)c;c=((ContextWrapper)c).getBaseContext();}throw new IllegalStateException("界面未连接到活动窗口");}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;backend.conversationVisible(true);activity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);main.post(tick);}
    @Override protected void onDetachedFromWindow(){attached=false;backend.conversationVisible(false);main.removeCallbacksAndMessages(null);for(Dialog dialog:new ArrayList<>(dialogs))dialog.dismiss();backend.close();worker.shutdownNow();super.onDetachedFromWindow();}
    /** One owner for keyboard geometry; a resized parent already has zero overlap. */
    static int keyboardOverlap(int viewBottom,int visibleBottom){return Math.max(0,viewBottom-visibleBottom);}
    private void layoutInsets(){
        if(!isAttachedToWindow())return;
        Rect visible=new Rect();getWindowVisibleDisplayFrame(visible);int[] position=new int[2];getLocationOnScreen(position);
        int extra=keyboardOverlap(position[1]+getHeight(),visible.bottom);
        if(extra<dp(80))extra=0; // Gesture/navigation bars are owned by the parent.
        WindowInsets insets=getRootWindowInsets();int top=insets==null?0:insets.getSystemWindowInsetTop();
        if(getPaddingBottom()!=extra||getPaddingTop()!=top)setPadding(0,top,0,extra);
    }
    public void refresh(){
        if(!attached)return;
        try{
            String id=(String)backend.state("CurrentChatId");String key="draft:"+(id==null?"none":id);
            if(!key.equals(draftKey)){
                String oldDraft=input.getText().toString();String oldKey=draftKey;draftKey=key;
                bindingDraft=true;String restored=prefs.getString(key,"");
                if(oldKey.equals("draft:none")&&!oldDraft.isEmpty()&&restored.isEmpty()){restored=oldDraft;prefs.edit().putString(key,restored).apply();}
                input.setText(restored);input.setSelection(restored.length());bindingDraft=false;if(!restored.isEmpty())backend.updateDraft(restored);
            }
            boolean busy=Boolean.TRUE.equals(backend.state("CurrentChatIsLoading"));
            String incoming=backend.incomingDraft();if(incoming!=null&&!incoming.contentEquals(input.getText())){bindingDraft=true;input.setText(incoming);input.setSelection(incoming.length());prefs.edit().putString(draftKey,incoming).apply();bindingDraft=false;}
            int attachments=backend.attachmentCount();attachmentBanner.setText("已添加 "+attachments+" 个附件");attachmentBanner.setVisibility(attachments>0?VISIBLE:GONE);
            Object error=backend.state("ErrorMessage");String message=error==null?"":error.toString();
            if(!message.isEmpty()){status.setText(message);status.setTextColor(0xffa74b4b);status.setVisibility(VISIBLE);lastError=message;}
            else if(busy||submitting){status.setText(submitting?"正在发送…":"Movness 正在处理…");status.setTextColor(MUTED);status.setVisibility(VISIBLE);}
            else if(!localError.isEmpty()){status.setText(localError);status.setTextColor(0xffa74b4b);status.setVisibility(VISIBLE);}
            else{status.setVisibility(GONE);lastError="";}
            renderMessages(id,busy);updateSend();
            JSONObject recording=LearningStore.sessionState(getContext());
            if(pendingLearning&&hasWindowFocus()&&LearningService.connected()){pendingLearning=false;beginDemonstration();}
            if(pendingAutomations&&hasWindowFocus()&&android.os.Build.VERSION.SDK_INT>=30&&android.os.Environment.isExternalStorageManager()){pendingAutomations=false;main.post(this::automations);}
            if(recording.optBoolean("recording")){learningBanner.setText("正在学习 · 已记录 "+recording.getJSONArray("steps").length()+" 步 · 点击结束");learningBanner.setOnClickListener(v->finishDemonstration());learningBanner.setVisibility(VISIBLE);}
            else{
                JSONObject replay=new JSONObject(LearningStore.status(getContext()));
                if(replay.optString("state").equals("running")){
                    learningBanner.setText("正在复用学习 · "+replay.optInt("completedSteps")+" / "+replay.optInt("totalSteps")+" 步 · 点击停止");
                    learningBanner.setOnClickListener(v->{try{LearningStore.cancel(getContext(),replay.getString("runId"));}catch(Exception e){failure("未能停止",e);}});learningBanner.setVisibility(VISIBLE);
                }else learningBanner.setVisibility(GONE);
            }
            if(!guideShown&&Boolean.TRUE.equals(backend.state("IsApiConfigInitialized"))&&!prefs.getBoolean("quick_start_v1",false)){
                guideShown=true;main.post(this::quickStart);
            }
        }catch(Exception e){failure("界面状态读取失败",e);}
    }
    private void renderMessages(String id,boolean busy){
        List<?> history=backend.messages();String conversation=id==null?"":id;
        if(!conversation.equals(lastChat)){messages.removeAllViews();bubbles.clear();rendered.clear();lastChat=conversation;forceBottom=true;}
        if(history.isEmpty()){
            if(messages.getChildCount()==0){LinearLayout empty=column();empty.setTag("movness.empty");empty.setPadding(dp(8),dp(70),dp(8),dp(30));empty.setGravity(Gravity.CENTER);
                ImageView mark=new ImageView(getContext());try(java.io.InputStream in=getContext().getAssets().open("operit.png")){mark.setImageBitmap(BitmapFactory.decodeStream(in));}catch(IOException error){throw new IllegalStateException("应用标识读取失败",error);}
                empty.addView(mark,new LayoutParams(dp(82),dp(82)));messages.addView(empty,new LayoutParams(-1,-1));
            }return;
        }
        if(messages.findViewWithTag("movness.empty")!=null){messages.removeAllViews();bubbles.clear();rendered.clear();}
        boolean nearBottom=scroll.getChildAt(0).getHeight()-scroll.getHeight()-scroll.getScrollY()<dp(120);boolean changed=false;
        for(Object item:history){
            String sender=(String)backend.value(item,"getSender");if(!sender.equals("user")&&!sender.equals("ai"))continue;
            String stamp=String.valueOf(backend.value(item,"getTimestamp"));String content=(String)backend.value(item,"getContent");
            Object stream=backend.value(item,"getContentStream");
            if(stream!=null){
                if(!streams.containsKey(stream)){streams.put(stream,"");backend.collectStream(stream,new Backend.StreamText(){
                    public void text(String text){main.post(()->{streams.put(stream,text);refresh();});}
                    public void error(Throwable error){main.post(()->failure("回复读取失败",error));}
                });}
                String live=streams.get(stream);if(!live.isEmpty())content=live;
            }
            String visible=ConversationText.visible(content);if(visible.isEmpty())continue;
            TextView bubble=bubbles.get(stamp);
            if(bubble==null){
                LinearLayout wrap=row();wrap.setGravity(sender.equals("user")?Gravity.END:Gravity.START);
                bubble=text("",17,INK);bubble.setTextIsSelectable(true);bubble.setTag("movness.message."+sender);bubble.setMaxWidth(getResources().getDisplayMetrics().widthPixels-dp(sender.equals("user")?70:40));
                bubble.setPadding(dp(sender.equals("user")?18:0),dp(14),dp(sender.equals("user")?18:0),dp(14));
                if(sender.equals("user"))bubble.setBackground(pill(PALE,25,PALE));
                wrap.addView(bubble,new LayoutParams(-2,-2));LayoutParams lp=new LayoutParams(-1,-2);lp.topMargin=dp(12);messages.addView(wrap,lp);bubbles.put(stamp,bubble);changed=true;
            }
            if(!visible.equals(rendered.get(stamp))){bubble.setText(sender.equals("user")?visible:ChatMarkdown.render(visible));if(sender.equals("ai"))bubble.setMovementMethod(LinkMovementMethod.getInstance());rendered.put(stamp,visible);changed=true;}
        }
        if(changed&&(nearBottom||forceBottom)){scroll.post(()->scroll.fullScroll(FOCUS_DOWN));forceBottom=false;}
    }
    private void updateSend(){boolean busy=Boolean.TRUE.equals(backend.state("CurrentChatIsLoading"));send.setImageDrawable(new LineIcon(busy?"stop":"send"));send.setContentDescription(busy?"停止当前响应":"发送");send.setEnabled(busy||(!submitting&&!input.getText().toString().trim().isEmpty()));send.setAlpha(send.isEnabled()?1f:.45f);}
    private void submit(){
        try{
            String id=(String)backend.state("CurrentChatId");
            if(Boolean.TRUE.equals(backend.state("CurrentChatIsLoading"))){if(id!=null)backend.stop(id);LearningStore.cancel(getContext());return;}
            if(submitting)return;
            if(!Boolean.TRUE.equals(backend.state("IsConfigured"))){modelDialog();return;}
            String text=input.getText().toString().trim();if(text.isEmpty())return;localError="";submitting=true;forceBottom=true;updateSend();
            if(id==null){backend.newConversation();waitForConversation(text,SystemClock.uptimeMillis()+15000);}
            else dispatch(text,id);
        }catch(Exception e){submitting=false;failure("消息未发送",e);}
    }
    private void waitForConversation(String text,long deadline){
        if(!attached){submitting=false;return;}
        String id=(String)backend.state("CurrentChatId");
        if(id!=null){dispatch(text,id);return;}
        if(SystemClock.uptimeMillis()>deadline){submitting=false;failure("消息未发送",new IllegalStateException("创建会话超时，请重试"));return;}
        main.postDelayed(()->waitForConversation(text,deadline),50);
    }
    private void dispatch(String text,String id){
        try{backend.send(text,id);input.setText("");prefs.edit().remove("draft:none").remove("draft:"+id).apply();}
        catch(Exception e){failure("消息未发送",e);}finally{submitting=false;refresh();}
    }
    private void connect(){try{hideKeyboard();computer.run();}catch(Exception e){failure("无法打开电脑连接",e);}}
    private void hideKeyboard(){((InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);input.clearFocus();}
    private Dialog sheet(String title,LinearLayout[] bodyOut){
        hideKeyboard();Dialog dialog=new Dialog(getContext());LinearLayout root=column();root.setPadding(dp(22),dp(12),dp(22),dp(22));root.setBackground(pill(BG,26,BORDER));
        LinearLayout heading=row();TextView t=text(title,22,INK);t.setTypeface(null,Typeface.BOLD);heading.addView(t,new LayoutParams(0,dp(52),1));heading.addView(icon("close","返回AI对话",dialog::dismiss),new LayoutParams(dp(44),dp(44)));root.addView(heading);
        ScrollView scroller=new ScrollView(getContext()){@Override protected void onMeasure(int width,int height){int cap=getResources().getDisplayMetrics().heightPixels-dp(170);int supplied=MeasureSpec.getSize(height);int limit=MeasureSpec.getMode(height)==MeasureSpec.UNSPECIFIED?cap:Math.min(cap,supplied);super.onMeasure(width,MeasureSpec.makeMeasureSpec(Math.max(dp(100),limit),MeasureSpec.AT_MOST));}};
        LinearLayout body=column();body.setPadding(0,dp(10),0,dp(5));scroller.addView(body);root.addView(scroller,new LayoutParams(-1,-2));bodyOut[0]=body;
        dialog.setContentView(root);Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setDimAmount(.25f);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
        dialogs.add(dialog);dialog.setOnDismissListener(closed->{dialogs.remove(dialog);if(dialog==guide)prefs.edit().putBoolean("quick_start_v1",true).apply();});
        dialog.show();if(w!=null){w.setLayout(getResources().getDisplayMetrics().widthPixels-dp(24),-2);w.setGravity(Gravity.BOTTOM);}
        return dialog;
    }
    private void item(LinearLayout body,String title,String detail,Runnable action){
        LinearLayout line=column();line.setPadding(dp(4),dp(15),dp(4),dp(15));line.addView(text(title,17,INK));if(!detail.isEmpty()){TextView d=text(detail,13,MUTED);LayoutParams lp=new LayoutParams(-1,-2);lp.topMargin=dp(5);line.addView(d,lp);}line.setOnClickListener(v->action.run());body.addView(line);
        View divider=new View(getContext());divider.setBackgroundColor(BORDER);body.addView(divider,new LayoutParams(-1,dp(1)));
    }
    private void menu(){LinearLayout[] b=new LinearLayout[1];Dialog d=sheet("Movness",b);
        item(b[0],"AI 对话","当前对话",d::dismiss);
        item(b[0],"对话记录","继续之前的对话",()->{d.dismiss();history();});
        item(b[0],"自动任务","让我按时间或条件执行",()->{d.dismiss();automations();});
        item(b[0],"示范学习","示范一次，下次复用",()->{d.dismiss();learning();});
        item(b[0],"连接 DSH","电脑配对与成果",()->{d.dismiss();connect();});
        item(b[0],"设置","模型与执行权限",()->{d.dismiss();settings();});
    }
    private void history(){LinearLayout[] b=new LinearLayout[1];Dialog d=sheet("对话记录",b);
        b[0].addView(primary("新对话",()->{backend.newConversation();d.dismiss();}));List<?> list=new ArrayList<>(backend.conversations());
        @SuppressWarnings("unchecked") List<Object> ordered=(List<Object>)list;ordered.sort((a,c)->Long.compare(((Number)backend.value(c,"getUpdatedAt")).longValue(),((Number)backend.value(a,"getUpdatedAt")).longValue()));
        if(ordered.isEmpty())b[0].addView(text("还没有对话记录",15,MUTED));
        for(Object chat:ordered){String id=(String)backend.value(chat,"getId");String title=(String)backend.value(chat,"getTitle");item(b[0],title,"",()->{backend.selectConversation(id);d.dismiss();refresh();});}
    }
    private void quickStart(){if(!attached)return;LinearLayout[] b=new LinearLayout[1];guide=sheet("开始使用 Movness",b);
        item(b[0],"接入你的模型","沿用现有配置，或接入自己的服务",this::modelDialog);
        item(b[0],"允许手机操作","开启手机操作与连续执行权限",this::settings);
        item(b[0],"连接电脑（可选）","扫码配对 DSH，继续电脑上的工作",()->{prefs.edit().putBoolean("quick_start_v1",true).apply();guide.dismiss();connect();});
        item(b[0],"教它一个操作（可选）","保存你的示范，下次复用",()->{prefs.edit().putBoolean("quick_start_v1",true).apply();guide.dismiss();learning();});
        b[0].addView(primary("进入 AI 对话",()->{prefs.edit().putBoolean("quick_start_v1",true).apply();guide.dismiss();}));
    }
    private void settings(){LinearLayout[] b=new LinearLayout[1];Dialog d=sheet("设置",b);
        item(b[0],"模型",String.valueOf(backend.state("ModelName")),this::modelDialog);
        Switch automatic=new Switch(getContext());automatic.setText("任务内连续执行");automatic.setTextSize(16);automatic.setTextColor(INK);automatic.setPadding(dp(4),dp(18),dp(4),dp(18));automatic.setEnabled(false);b[0].addView(automatic);
        TextView explain=text("在已开启的系统权限和工具授权范围内连续工作。",13,MUTED);b[0].addView(explain);
        worker.execute(()->{try{boolean enabled=backend.automaticMode(getContext());main.post(()->{automatic.setChecked(enabled);automatic.setEnabled(true);automatic.setOnCheckedChangeListener((v,on)->{
            automatic.setEnabled(false);worker.execute(()->{try{backend.setAutomaticMode(getContext(),on);main.post(()->automatic.setEnabled(true));}catch(Exception e){main.post(()->{failure("权限未保存",e);automatic.setEnabled(true);});}});
        });});}catch(Exception e){main.post(()->failure("权限读取失败",e));}});
        item(b[0],"手机操作与学习权限",LearningService.connected()?"已开启":"尚未开启",this::learningPermission);
        item(b[0],"首次使用引导","重新查看模型、手机与电脑连接步骤",()->{d.dismiss();quickStart();});
        item(b[0],"关于 Movness","版本 0.5.2 · 开源来源与许可",this::about);
    }
    private EditText field(LinearLayout body,String label,String value,boolean secret){
        TextView caption=text(label,13,MUTED);LayoutParams cp=new LayoutParams(-1,-2);cp.topMargin=dp(14);body.addView(caption,cp);
        EditText e=new EditText(getContext());e.setTextSize(15);e.setTextColor(INK);e.setText(value);e.setSingleLine(true);e.setIncludeFontPadding(false);e.setGravity(Gravity.CENTER_VERTICAL);e.setPadding(dp(14),0,dp(14),0);e.setBackground(pill(0xffffffff,13,BORDER));
        if(secret)e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);LayoutParams lp=new LayoutParams(-1,dp(50));lp.topMargin=dp(7);body.addView(e,lp);return e;
    }
    private void modelDialog(){
        LinearLayout[] b=new LinearLayout[1];Dialog d=sheet("接入模型",b);Object active=backend.state("ActiveChatModelConfig");String id=active==null?null:(String)backend.value(active,"getId");
        if(id==null){b[0].addView(text("模型配置还在初始化，请稍后打开",15,MUTED));return;}
        String originalKey=(String)backend.state("ApiKey");String protocol=backend.state("ApiProviderType").toString();
        List<String> protocolIds=new ArrayList<>();List<String> labels=new ArrayList<>();protocolIds.add(protocol);labels.add(protocolName(protocol));
        for(String s:new String[]{"OPENAI_GENERIC","OPENAI_RESPONSES_GENERIC","ANTHROPIC_GENERIC","DEEPSEEK","GEMINI_GENERIC"})if(!protocolIds.contains(s)){protocolIds.add(s);labels.add(s.equals("OPENAI_GENERIC")?"OpenAI 兼容":s.equals("OPENAI_RESPONSES_GENERIC")?"OpenAI Responses":s.equals("ANTHROPIC_GENERIC")?"Claude 兼容":s.equals("DEEPSEEK")?"DeepSeek":"Gemini 兼容");}
        Spinner provider=new Spinner(getContext());provider.setAdapter(new ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,labels));b[0].addView(provider,new LayoutParams(-1,dp(48)));
        EditText endpoint=field(b[0],"服务地址",(String)backend.state("ApiEndpoint"),false);endpoint.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        EditText key=field(b[0],"API 密钥",originalKey,true);EditText model=field(b[0],"模型",(String)backend.state("ModelName"),false);
        b[0].addView(primary("保存",()->{
            String url=endpoint.getText().toString().trim(),token=key.getText().toString().trim(),name=model.getText().toString().trim();
            if(!(url.startsWith("https://")||url.startsWith("http://"))||name.isEmpty()){failure("配置未保存",new IllegalArgumentException("请填写完整服务地址和模型名称"));return;}
            String selected=protocolIds.get(provider.getSelectedItemPosition());worker.execute(()->{
                try{backend.saveConfiguration(getContext(),id,token,url,name,selected,!token.equals(originalKey));main.post(()->{d.dismiss();Toast.makeText(getContext(),"模型配置已保存",Toast.LENGTH_SHORT).show();refresh();});}
                catch(Exception e){main.post(()->failure("模型配置未保存",e));}
            });
        }));
    }
    private void automations(){LinearLayout[] b=new LinearLayout[1];Dialog d=sheet("自动任务",b);
        b[0].addView(text("在 AI 对话里告诉我何时、做什么。\n这里显示已保存的自动化任务。",15,MUTED));
        if(android.os.Build.VERSION.SDK_INT>=30&&!android.os.Environment.isExternalStorageManager()){
            b[0].addView(text("自动任务需要保存到手机文件夹，请先开启文件访问权限。",14,MUTED));
            b[0].addView(primary("允许保存自动任务",()->{pendingAutomations=true;d.dismiss();getContext().startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,android.net.Uri.parse("package:"+getContext().getPackageName())));}));
            return;
        }
        b[0].addView(primary("回到对话创建",d::dismiss));
        worker.execute(()->{try{
            List<JSONObject> values=backend.automaticTasks(getContext());
            main.post(()->{if(values.isEmpty())b[0].addView(text("还没有自动任务",15,MUTED));for(JSONObject v:values)item(b[0],v.optString("name"),(v.optBoolean("enabled")?"已启用":"已暂停")+" · "+v.optString("description"),()->new AlertDialog.Builder(getContext()).setTitle(v.optString("name")).setMessage(v.optString("description")+"\n\n累计执行："+v.optInt("totalExecutions")+" 次\n成功："+v.optInt("successfulExecutions")+" 次\n失败："+v.optInt("failedExecutions")+" 次").setPositiveButton("关闭",null).show());});
        }catch(Exception e){main.post(()->failure("自动任务读取失败",e));}});
    }
    private void learning(){LinearLayout[] b=new LinearLayout[1];Dialog d=sheet("示范学习",b);
        b[0].addView(text("示范一次操作，保存后可让 Movness 再做一次。",15,MUTED));
        b[0].addView(primary("开始示范",()->{d.dismiss();try{if(!LearningService.connected()){pendingLearning=true;learningPermission();}else beginDemonstration();}catch(Exception e){failure("无法开始示范",e);}}));
        try{
            JSONObject unfinished=LearningStore.sessionState(getContext());if(!unfinished.optBoolean("recording")&&unfinished.has("steps")&&unfinished.getJSONArray("steps").length()>0)
                b[0].addView(button("继续保存上次示范",()->{d.dismiss();finishDemonstration();}));
            JSONArray list=new JSONObject(LearningStore.list(getContext())).getJSONArray("skills");
            if(list.length()==0)b[0].addView(text("还没有学习记录",15,MUTED));
            for(int i=0;i<list.length();i++){JSONObject skill=list.getJSONObject(i);item(b[0],skill.getString("name"),skill.getInt("steps")+" 步",()->skill(d,skill));}
        }catch(Exception e){failure("学习记录读取失败",e);}
    }
    private String protocolName(String value){switch(value){case "OPENAI":case "OPENAI_GENERIC":return "OpenAI 兼容";case "OPENAI_RESPONSES":case "OPENAI_RESPONSES_GENERIC":return "OpenAI Responses";case "ANTHROPIC":case "ANTHROPIC_GENERIC":return "Claude 兼容";case "DEEPSEEK":return "DeepSeek";case "GOOGLE":case "GEMINI_GENERIC":return "Gemini 兼容";default:return value;}}
    private void learningPermission(){Intent intent=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);intent.putExtra(":settings:fragment_args_key",new ComponentName(getContext(),LearningService.class).flattenToString());getContext().startActivity(intent);}
    private void beginDemonstration(){try{LearningStore.start(getContext());hideKeyboard();getContext().startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));Toast.makeText(getContext(),"开始示范，完成后回到 Movness 点击结束",Toast.LENGTH_LONG).show();}catch(Exception e){failure("无法开始示范",e);}}
    private void finishDemonstration(){
        try{JSONObject session=LearningStore.finish(getContext());JSONArray steps=session.getJSONArray("steps");LinearLayout[] b=new LinearLayout[1];Dialog d=sheet("保存这次学习",b);
            b[0].addView(text("已记录 "+steps.length()+" 步"+(session.optInt("skipped")>0?"，另有 "+session.getInt("skipped")+" 次操作未捕获":""),14,MUTED));
            EditText name=field(b[0],"操作名称","",false);name.setHint("例如：打开收藏列表");
            for(int i=0;i<Math.min(steps.length(),30);i++){JSONObject s=steps.getJSONObject(i);b[0].addView(text((i+1)+". "+stepDescription(s),13,MUTED));}
            b[0].addView(primary("保存",()->{try{LearningStore.save(getContext(),name.getText().toString());d.dismiss();Toast.makeText(getContext(),"学习记录已保存",Toast.LENGTH_SHORT).show();}catch(Exception e){failure("未保存",e);}}));
            b[0].addView(button("丢弃这次示范",()->{try{LearningStore.discard(getContext());d.dismiss();}catch(Exception e){failure("未能丢弃",e);}}));
        }catch(Exception e){failure("无法结束示范",e);}
    }
    private String stepDescription(JSONObject step){
        String action=step.optString("action"),label="";
        switch(action){case "launch":action="打开应用";break;case "click":action="点击";break;case "longClick":action="长按";break;case "text":action="填写文字";break;case "scrollForward":action="向下滚动";break;case "scrollBackward":action="向上滚动";break;}
        for(String key:new String[]{"label","description","viewId","package"})if(!step.optString(key).isEmpty()){label=step.optString(key);break;}
        if(step.optString("action").equals("launch"))try{label=getContext().getPackageManager().getApplicationLabel(getContext().getPackageManager().getApplicationInfo(step.optString("package"),0)).toString();}catch(android.content.pm.PackageManager.NameNotFoundException missing){label=step.optString("package");}
        return action+" · "+label;
    }
    private void skill(Dialog list,JSONObject summary){
        try{JSONObject s=LearningStore.skill(getContext(),summary.getString("id"));LinearLayout[] b=new LinearLayout[1];Dialog d=sheet(s.getString("name"),b);
            b[0].addView(text("已保存 "+s.getJSONArray("steps").length()+" 步。界面变化或控件不明确时会停止。",14,MUTED));
            b[0].addView(primary("照着做一次",()->{try{LearningStore.run(getContext(),s.getString("id"));d.dismiss();list.dismiss();Toast.makeText(getContext(),"开始执行学习记录",Toast.LENGTH_SHORT).show();}catch(Exception e){failure("未能执行",e);}}));
            b[0].addView(button("让 AI 使用这次学习",()->{input.setText("使用我保存的学习记录「"+s.optString("name")+"」（编号 "+s.optString("id")+"）完成操作，核对结果后告诉我。");d.dismiss();list.dismiss();}));
            b[0].addView(button("查看执行结果",()->{try{new AlertDialog.Builder(getContext()).setTitle("执行结果").setMessage(new JSONObject(LearningStore.status(getContext())).optString("message","尚未执行")).setPositiveButton("关闭",null).show();}catch(Exception e){failure("无法读取执行结果",e);}}));
            b[0].addView(button("删除学习记录",()->new AlertDialog.Builder(getContext()).setTitle("删除这条学习记录？").setNegativeButton("保留",null).setPositiveButton("删除",(dialog,which)->{try{LearningStore.delete(getContext(),s.getString("id"));d.dismiss();list.dismiss();learning();}catch(Exception e){failure("删除失败",e);}}).show()));
        }catch(Exception e){failure("无法打开学习记录",e);}
    }
    private void about(){LinearLayout[] b=new LinearLayout[1];sheet("关于 Movness",b);b[0].addView(text("Movness 是手机里的个人行动助手。\n\n产品与交互：jon\n开源底座：Operit（LGPL-3.0）\n电脑协作：DSH 与原远程插件\n\n保留各组件的原作者、来源与许可证。",15,INK));
        item(b[0],"开源底座源码","github.com/AAswordman/Operit",()->getContext().startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/AAswordman/Operit"))));
        item(b[0],"本版许可说明","查看随安装包提供的原始许可证",()->{try{java.io.InputStream in=getContext().getAssets().open("movness-notices.txt");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] data=new byte[4096];int n;while((n=in.read(data))!=-1)out.write(data,0,n);in.close();LinearLayout[] license=new LinearLayout[1];sheet("开源许可",license);TextView t=text(out.toString("UTF-8"),12,INK);t.setTextIsSelectable(true);license[0].addView(t);}catch(Exception e){failure("许可说明读取失败",e);}});
    }
    private void failure(String title,Throwable error){android.util.Log.e("MovnessUI",title,error);localError=title+"："+(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());status.setText(localError);status.setTextColor(0xffa74b4b);status.setVisibility(VISIBLE);Toast.makeText(getContext(),localError,Toast.LENGTH_LONG).show();}
    private final class LineIcon extends Drawable{
        final String type;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);LineIcon(String type){this.type=type;}
        public void draw(Canvas c){Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);p.setColor(type.equals("send")||type.equals("stop")?Color.WHITE:BLUE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            if(type.equals("send")){Path path=new Path();path.moveTo(5,11);path.lineTo(12,4);path.lineTo(19,11);c.drawPath(path,p);c.drawLine(12,5,12,20,p);}
            else if(type.equals("stop")){p.setStyle(Paint.Style.FILL);c.drawRoundRect(6,6,18,18,2,2,p);}
            else if(type.equals("close")){c.drawLine(6,6,18,18,p);c.drawLine(18,6,6,18,p);}
            else{c.drawLine(4,6,20,6,p);c.drawLine(4,12,20,12,p);c.drawLine(4,18,20,18,p);}c.restore();}
        public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
}
