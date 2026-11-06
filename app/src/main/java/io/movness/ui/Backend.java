package io.movness.ui;

import java.util.List;

/** The existing agent owns conversations, tools, providers and automation. */
public interface Backend {
    Object state(String property);
    List<?> messages();
    List<?> conversations();
    Object value(Object object, String getter);
    void newConversation();
    void selectConversation(String id);
    void send(String text, String id);
    void stop(String id);
    void collectStream(Object stream, StreamText callback);
    void close();
    void conversationVisible(boolean visible);
    String incomingDraft();
    void updateDraft(String text);
    int attachmentCount();
    boolean automaticMode(android.content.Context context) throws Exception;
    void setAutomaticMode(android.content.Context context,boolean enabled) throws Exception;
    void saveConfiguration(android.content.Context context,String id,String key,String endpoint,String model,String protocol,boolean changed) throws Exception;
    java.util.List<org.json.JSONObject> automaticTasks(android.content.Context context) throws Exception;
    interface StreamText { void text(String text); void error(Throwable error); }
}
