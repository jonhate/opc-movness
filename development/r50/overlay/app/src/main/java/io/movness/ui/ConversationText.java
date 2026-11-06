package io.movness.ui;

/** Removes internal tool payloads from the conversation surface, including unfinished streams. */
public final class ConversationText {
    private ConversationText(){}
    public static String visible(String input){
        String s=input;
        for(String tag:new String[]{"tool","tool_result","tool_response","thinking","think"}){
            s=s.replaceAll("(?is)<"+tag+"(?:\\s[^>]*)?>.*?</"+tag+"\\s*>","");
            s=s.replaceAll("(?is)<"+tag+"(?:\\s[^>]*)?>.*$","");
            s=s.replaceAll("(?is)<"+tag+"(?:\\s[^>]*)?$","");
        }
        return s.trim();
    }
}
