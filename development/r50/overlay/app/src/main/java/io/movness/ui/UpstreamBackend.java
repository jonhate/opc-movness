package io.movness.ui;

import android.content.Context;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import kotlin.Unit;
import kotlin.ResultKt;
import kotlin.coroutines.*;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.*;
import kotlinx.coroutines.flow.StateFlow;

/** Binds the pinned host ABI. Missing methods fail explicitly; no substitute executor. */
public final class UpstreamBackend implements Backend {
    private static final String ROOT="com.ai.assistance.operit.";
    private final Object vm;
    private final CoroutineScope streams=CoroutineScopeKt.MainScope();
    private final Set<String> subscribed=ConcurrentHashMap.newKeySet();
    private String lastDraft;
    public UpstreamBackend(Object vm) { this.vm=Objects.requireNonNull(vm); }
    public static Class<?> type(String name) {
        try { return Class.forName(name); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("宿主接口不可用："+name,e); }
    }
    public static Object call(Object target,String name,Class<?>[] types,Object... args) {
        try { Method method=target.getClass().getMethod(name,types);method.setAccessible(true);return method.invoke(target,args); }
        catch (InvocationTargetException e) { throw propagate(e.getCause()); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("宿主接口不可用："+name,e); }
    }
    public static RuntimeException propagate(Throwable e) {
        if(e instanceof RuntimeException)return (RuntimeException)e;
        return new IllegalStateException(e.getMessage(),e);
    }
    public static Object object(String name) {
        try{return type(name).getField("INSTANCE").get(null);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(name,e);}
    }
    public static Object enumValue(String name,String value) {
        @SuppressWarnings({"unchecked","rawtypes"}) Object v=Enum.valueOf((Class)type(name),value);return v;
    }
    public static Object companion(String name) {
        try{return type(name).getField("Companion").get(null);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(name,e);}
    }
    public Object state(String property) {
        String getter=property.equals("IsConfigured")?"isConfigured":property.equals("IsApiConfigInitialized")?"isApiConfigInitialized":"get"+property;
        StateFlow<?> flow=(StateFlow<?>)value(vm,getter);
        if(subscribed.add(property))kotlinx.coroutines.flow.FlowKt.launchIn(
            kotlinx.coroutines.flow.FlowKt.onEach(flow,(v,c)->Unit.INSTANCE),streams);
        return flow.getValue();
    }
    public Object value(Object object,String getter) {return call(object,getter,new Class<?>[0]);}
    public List<?> messages(){return (List<?>)state("ChatHistory");}
    public List<?> conversations(){return (List<?>)state("ChatHistories");}
    public void newConversation(){call(vm,"createNewChat",new Class<?>[]{String.class,String.class},null,null);}
    public void selectConversation(String id){call(vm,"switchChat",new Class<?>[]{String.class},id);}
    public void send(String text,String id){
        Class<?> mode=type(ROOT+"data.model.PromptFunctionType");
        call(vm,"sendTextMessage",new Class<?>[]{String.class,String.class,mode},text,id,enumValue(mode.getName(),"CHAT"));
    }
    public void stop(String id){call(vm,"cancelMessage",new Class<?>[]{String.class},id);}
    public void close(){CoroutineScopeKt.cancel(streams,null);}
    public void conversationVisible(boolean visible){call(object(ROOT+"ui.main.screens.GestureStateHolder"),"setChatScreenGestureConsumed",new Class<?>[]{boolean.class},visible);}
    public int attachmentCount(){return ((List<?>)state("Attachments")).size();}
    public String incomingDraft(){
        Object share=object(ROOT+"ui.main.SharedFileHandler");
        Object files=((StateFlow<?>)value(share,"getSharedFiles")).getValue();
        String shared=(String)((StateFlow<?>)value(share,"getSharedText")).getValue();
        if(files!=null){
            String caption=(String)((StateFlow<?>)value(share,"getSharedFileText")).getValue();
            call(vm,"handleSharedFiles",new Class<?>[]{List.class,String.class,String.class},files,state("CurrentChatId"),caption);
            call(share,"clearSharedFiles",new Class<?>[0]);
        }
        if(shared!=null){call(vm,"handleSharedText",new Class<?>[]{String.class,String.class},shared,state("CurrentChatId"));call(share,"clearSharedText",new Class<?>[0]);}
        Object pending=object(ROOT+"ui.main.PendingChatDraftHandler");String draft=(String)((StateFlow<?>)value(pending,"getPendingDraft")).getValue();
        if(draft!=null){call(vm,"handleSharedText",new Class<?>[]{String.class,String.class},draft,state("CurrentChatId"));call(pending,"clearPendingDraft",new Class<?>[0]);}
        String text=(String)value(state("UserMessage"),"getText");
        if(!Objects.equals(text,lastDraft)){lastDraft=text;return text;}return null;
    }
    public void updateDraft(String text){
        try{
            Class<?> value=type("androidx.compose.ui.text.input.TextFieldValue");
            Object draft=value.getConstructor(String.class,long.class,type("androidx.compose.ui.text.TextRange"),int.class,type("kotlin.jvm.internal.DefaultConstructorMarker")).newInstance(text,0L,null,6,null);
            lastDraft=text;call(vm,"updateUserMessage",new Class<?>[]{value},draft);
        }catch(ReflectiveOperationException e){throw new IllegalStateException("输入状态未保存",e);}
    }
    public void collectStream(Object stream,StreamText callback){
        final StringBuilder text=new StringBuilder();
        CoroutineExceptionHandler errors=CoroutineExceptionHandlerKt.CoroutineExceptionHandler((context,error)->{
            if(!(error instanceof java.util.concurrent.CancellationException))callback.error(error);return Unit.INSTANCE;
        });
        BuildersKt.launch(streams,errors,CoroutineStart.DEFAULT,
            new Function2<CoroutineScope,Continuation<? super Unit>,Object>(){
                public Object invoke(CoroutineScope scope,Continuation<? super Unit> continuation){
                    Class<?> collector=type(ROOT+"util.stream.StreamCollector");
                    Object sink=Proxy.newProxyInstance(collector.getClassLoader(),new Class<?>[]{collector},(proxy,method,args)->{
                        if(method.getName().equals("emit")){
                            text.append((String)args[0]);callback.text(text.toString());return Unit.INSTANCE;
                        }
                        if(method.getName().equals("toString"))return "MovnessStreamCollector";
                        if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                        if(method.getName().equals("equals"))return proxy==args[0];
                        throw new UnsupportedOperationException(method.getName());
                    });
                    try{return call(stream,"collect",new Class<?>[]{collector,Continuation.class},sink,continuation);}
                    catch(Exception e){callback.error(e);return Unit.INSTANCE;}
                }
            });
    }
    /** Executes a real Kotlin suspend API on a worker, including its failure result. */
    public static Object await(Object target,String method,Class<?>[] types,Object... args)throws Exception{
        CountDownLatch done=new CountDownLatch(1);Object[] output=new Object[1];Throwable[] failure=new Throwable[1];
        Continuation<Object> continuation=new Continuation<Object>(){
            public CoroutineContext getContext(){return EmptyCoroutineContext.INSTANCE;}
            public void resumeWith(Object result){
                try{ResultKt.throwOnFailure(result);output[0]=result;}
                catch(Throwable e){failure[0]=e;}finally{done.countDown();}
            }
        };
        Class<?>[] signature=Arrays.copyOf(types,types.length+1);signature[types.length]=Continuation.class;
        Object[] values=Arrays.copyOf(args,args.length+1);values[args.length]=continuation;
        Object result=call(target,method,signature,values);
        if(result!=IntrinsicsKt.getCOROUTINE_SUSPENDED())return result;
        if(!done.await(30,TimeUnit.SECONDS))throw new TimeoutException("本机配置保存超时，请检查后重试");
        if(failure[0]!=null)throw propagate(failure[0]);
        return output[0];
    }
    public static Object configManager(Context context)throws Exception{
        return type(ROOT+"data.preferences.ModelConfigManager").getConstructor(Context.class,
            type("androidx.datastore.core.DataStore"),int.class,type("kotlin.jvm.internal.DefaultConstructorMarker"))
            .newInstance(context.getApplicationContext(),null,2,null);
    }
    public static void saveModel(Context context,String id,String key,String endpoint,String model,String protocol,boolean keyChanged,boolean protocolChanged)throws Exception{
        Object manager=configManager(context);Class<?> provider=type(ROOT+"data.model.ApiProviderType");
        if(protocolChanged)await(manager,"updateModelConfig",new Class<?>[]{String.class,String.class,String.class,String.class,provider,String.class},id,key,endpoint,model,enumValue(provider.getName(),protocol),protocol);
        else await(manager,"updateModelConfig",new Class<?>[]{String.class,String.class,String.class,String.class},id,key,endpoint,model);
        if(keyChanged)await(manager,"updateSingleApiKey",new Class<?>[]{String.class,String.class},id,key);
    }
    public static Object permissions(Context context){
        return call(companion(ROOT+"ui.permissions.ToolPermissionSystem"),"getInstance",new Class<?>[]{Context.class},context.getApplicationContext());
    }
    public static void automatic(Context context,boolean enabled)throws Exception{
        Class<?> level=type(ROOT+"ui.permissions.PermissionLevel");
        await(permissions(context),"saveMasterSwitch",new Class<?>[]{level},enumValue(level.getName(),enabled?"ALLOW":"ASK"));
    }
    public static boolean isAutomatic(Context context)throws Exception{
        Object flow=call(permissions(context),"getMasterSwitchFlow",new Class<?>[0]);
        Object result=awaitStaticFlowFirst(flow);
        return result.toString().equals("ALLOW");
    }
    public boolean automaticMode(Context context)throws Exception{return isAutomatic(context);}
    public void setAutomaticMode(Context context,boolean enabled)throws Exception{automatic(context,enabled);}
    public void saveConfiguration(Context context,String id,String key,String endpoint,String model,String protocol,boolean changed)throws Exception{saveModel(context,id,key,endpoint,model,protocol,changed,!protocol.equals(state("ApiProviderType").toString()));}
    public List<org.json.JSONObject> automaticTasks(Context context)throws Exception{
        Object repository=type(ROOT+"data.repository.WorkflowRepository").getConstructor(Context.class).newInstance(context.getApplicationContext());
        Method method=repository.getClass().getDeclaredMethod("getWorkflowDirectory");method.setAccessible(true);
        java.io.File directory=(java.io.File)method.invoke(repository);java.io.File[] files=directory.listFiles();
        if(files==null)throw new java.io.IOException("无法读取自动任务目录");List<org.json.JSONObject> result=new ArrayList<>();
        for(java.io.File f:files)if(f.isFile()&&f.getName().endsWith(".json")){
            org.json.JSONObject workflow=new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()),java.nio.charset.StandardCharsets.UTF_8));
            org.json.JSONArray nodes=workflow.getJSONArray("nodes");boolean automatic=false;
            for(int i=0;i<nodes.length();i++){String trigger=nodes.getJSONObject(i).optString("triggerType");if(Arrays.asList("schedule","tasker","intent","speech","app_open").contains(trigger))automatic=true;}
            if(automatic)result.add(workflow);
        }
        return result;
    }
    private static Object awaitStaticFlowFirst(Object flow)throws Exception{
        // FlowKt.first is a suspend extension compiled as a static method.
        CountDownLatch done=new CountDownLatch(1);Object[] value=new Object[1];Throwable[] error=new Throwable[1];
        Continuation<Object> c=new Continuation<Object>(){
            public CoroutineContext getContext(){return EmptyCoroutineContext.INSTANCE;}
            public void resumeWith(Object result){try{ResultKt.throwOnFailure(result);value[0]=result;}catch(Throwable e){error[0]=e;}finally{done.countDown();}}
        };
        Object r=kotlinx.coroutines.flow.FlowKt.first((kotlinx.coroutines.flow.Flow<?>)flow,c);
        if(r!=IntrinsicsKt.getCOROUTINE_SUSPENDED())return r;
        if(!done.await(15,TimeUnit.SECONDS))throw new TimeoutException("权限状态读取超时");
        if(error[0]!=null)throw propagate(error[0]);return value[0];
    }
}
