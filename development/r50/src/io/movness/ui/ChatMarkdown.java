package io.movness.ui;
import android.text.Html;
import android.text.Spanned;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter;
import org.commonmark.renderer.NodeRenderer;
import java.util.*;
/** Reuses the same CommonMark parser family as the earlier Movness native reader. */
final class ChatMarkdown {
    private static final Parser PARSER=Parser.builder().build();
    private static final HtmlRenderer HTML=HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).softbreak("<br />")
        .nodeRendererFactory(context->new NodeRenderer(){
            public Set<Class<? extends Node>> getNodeTypes(){return Collections.singleton(ListItem.class);}
            public void render(Node node){
                HtmlWriter html=context.getWriter();int number=1;
                if(node.getParent() instanceof OrderedList){number=((OrderedList)node.getParent()).getStartNumber();for(Node previous=node.getPrevious();previous!=null;previous=previous.getPrevious())number++;}
                html.line();html.tag("div");html.text(node.getParent() instanceof OrderedList?number+". ":"• ");
                for(Node child=node.getFirstChild();child!=null;child=child.getNext()){
                    if(child instanceof Paragraph){for(Node inline=child.getFirstChild();inline!=null;inline=inline.getNext())context.render(inline);}
                    else context.render(child);
                }
                html.tag("/div");html.line();
            }
        }).build();
    static Spanned render(String text){return Html.fromHtml(HTML.render(PARSER.parse(text)),Html.FROM_HTML_MODE_COMPACT);}
}
