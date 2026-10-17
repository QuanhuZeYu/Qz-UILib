package club.heiqi.uilib.host.media.bilibili;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 平台响应的有限JSON读取；不把网络输入交给YAML对象构造或脚本引擎。 */
final class BilibiliJson {
    private final String text;
    private int at,nodes;
    private BilibiliJson(String text){this.text=text;}
    static Object parse(String text)throws IOException{
        BilibiliJson parser=new BilibiliJson(text);Object result=parser.value(0);parser.space();
        if(parser.at!=text.length())throw parser.error();return result;
    }
    private Object value(int depth)throws IOException{
        space();if(depth>64||++nodes>200000||at>=text.length())throw error();char c=text.charAt(at);
        if(c=='"')return string();
        if(c=='{'){
            at++;Map<String,Object> result=new LinkedHashMap<>();space();if(take('}'))return result;
            do{space();if(at>=text.length()||text.charAt(at)!='"')throw error();String key=string();space();need(':');if(result.containsKey(key))throw error();result.put(key,value(depth+1));space();if(take('}'))return result;need(',');}while(true);
        }
        if(c=='['){at++;List<Object> result=new ArrayList<>();space();if(take(']'))return result;do{result.add(value(depth+1));space();if(take(']'))return result;need(',');}while(true);}
        if(text.startsWith("true",at)){at+=4;return Boolean.TRUE;}if(text.startsWith("false",at)){at+=5;return Boolean.FALSE;}if(text.startsWith("null",at)){at+=4;return null;}
        int start=at;if(take('-')&&at==text.length())throw error();
        if(take('0')){if(at<text.length()&&Character.isDigit(text.charAt(at)))throw error();}else{if(at>=text.length()||text.charAt(at)<'1'||text.charAt(at)>'9')throw error();while(digit())at++;}
        boolean decimal=false;if(take('.')){decimal=true;if(!digit())throw error();while(digit())at++;}
        if(take('e')||take('E')){decimal=true;if(!take('+'))take('-');if(!digit())throw error();while(digit())at++;}
        try{String number=text.substring(start,at);if(decimal){double d=Double.parseDouble(number);if(!Double.isFinite(d))throw error();return d;}return Long.parseLong(number);}catch(NumberFormatException failure){throw error();}
    }
    private boolean digit(){return at<text.length()&&text.charAt(at)>='0'&&text.charAt(at)<='9';}
    private String string()throws IOException{
        need('"');StringBuilder result=new StringBuilder();
        while(at<text.length()){
            char c=text.charAt(at++);if(c=='"')return result.toString();if(c<32)throw error();
            if(c=='\\'){
                if(at==text.length())throw error();c=text.charAt(at++);
                switch(c){case '"':case '\\':case '/':break;case 'b':c='\b';break;case 'f':c='\f';break;case 'n':c='\n';break;case 'r':c='\r';break;case 't':c='\t';break;
                    case 'u':if(at+4>text.length())throw error();try{c=(char)Integer.parseInt(text.substring(at,at+4),16);}catch(NumberFormatException e){throw error();}at+=4;break;default:throw error();}
            }
            result.append(c);
        }
        throw error();
    }
    private void space(){while(at<text.length()&&(text.charAt(at)==' '||text.charAt(at)=='\n'||text.charAt(at)=='\r'||text.charAt(at)=='\t'))at++;}
    private boolean take(char c){if(at<text.length()&&text.charAt(at)==c){at++;return true;}return false;}
    private void need(char c)throws IOException{if(!take(c))throw error();}
    private IOException error(){return new IOException("Bilibili JSON响应无效或超过结构预算，位置 "+at);}
}
