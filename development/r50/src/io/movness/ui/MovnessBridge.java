package io.movness.ui;

import android.content.Context;
import androidx.compose.runtime.Composer;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.viewinterop.AndroidView_androidKt;
import androidx.compose.foundation.layout.SizeKt;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import java.util.*;

/** Hosts Movness's native input and navigation in the existing AI conversation route. */
public final class MovnessBridge {
    private MovnessBridge(){}
    public static void render(Object viewModel,Function0<?> workspace,Function0<?> model,
            Function0<?> permissions,Function0<?> computer,Composer composer,int changed){
        Function1<Context,MovnessView> factory=context->new MovnessView(context,new UpstreamBackend(viewModel),
            ()->computer.invoke(),()->permissions.invoke());
        Function1<MovnessView,Unit> update=view->{view.refresh();return Unit.INSTANCE;};
        AndroidView_androidKt.AndroidView(factory,SizeKt.fillMaxSize(Modifier.Companion,1f),update,composer,0,0);
    }
    public static void openComputer(Function1<Object,?> navigate){
        try{
            Object screen=UpstreamBackend.type("com.ai.assistance.operit.ui.main.screens.Screen$ToolPkgComposeDsl")
                .getConstructor(String.class,String.class,String.class,boolean.class)
                .newInstance("io.movness.computer","computer_connect","连接 DSH",false);
            navigate.invoke(screen);
        }catch(ReflectiveOperationException e){throw new IllegalStateException("电脑连接入口不可用",e);}
    }
    /** Hide upstream configuration routes without deleting their implementation or data. */
    public static List<?> navigation(List<?> items){
        List<Object> result=new ArrayList<>();
        for(Object item:items){String route=(String)UpstreamBackend.call(item,"getRoute",new Class<?>[0]);
            if(route.equals("ai_chat")||route.equals("about"))result.add(item);
        }
        return result;
    }
    @SuppressWarnings("unchecked")
    public static android.view.View drawerView(Context context,Function1<?,?> callback){
        Function1<Object,?> navigate=(Function1<Object,?>)callback;
        android.widget.LinearLayout column=new android.widget.LinearLayout(context);column.setOrientation(1);
        int padding=Math.round(22*context.getResources().getDisplayMetrics().density);column.setPadding(padding,padding*2,padding,padding);
        android.widget.TextView title=new android.widget.TextView(context);title.setText("Movness");title.setTextSize(25);title.setTextColor(MovnessView.INK);column.addView(title);
        for(String label:new String[]{"AI 对话","连接 DSH","关于 Movness"}){
            android.widget.TextView button=new android.widget.TextView(context);button.setText(label);button.setTextSize(17);button.setTextColor(MovnessView.INK);button.setPadding(0,padding,0,padding);column.addView(button);
            button.setOnClickListener(v->{if(label.equals("连接 DSH"))openComputer(navigate);else navigate.invoke(UpstreamBackend.object("com.ai.assistance.operit.ui.main.screens.Screen$"+(label.equals("AI 对话")?"AiChat":"About")));});
        }
        return column;
    }
    public static void renderDrawer(Function1<?,?> navigate,Composer composer){
        Function1<Context,android.view.View> factory=context->drawerView(context,navigate);
        Function1<android.view.View,Unit> update=view->Unit.INSTANCE;
        AndroidView_androidKt.AndroidView(factory,SizeKt.fillMaxSize(Modifier.Companion,1f),update,composer,0,0);
    }
}
