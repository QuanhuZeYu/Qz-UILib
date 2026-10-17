package club.heiqi.uilib.ui.scene.control;

import java.util.Comparator;

/** 目录始终优先；自然名称比较不解析为固定宽度数字，也不改变实际Path身份。 */
final class FilePickerOrder implements Comparator<FilePickerDirectory.Entry> {
    static final FilePickerOrder NAME=new FilePickerOrder(0,false,"名称 ↑"),NAME_DESC=new FilePickerOrder(0,true,"名称 ↓"),SIZE=new FilePickerOrder(1,false,"大小 ↑"),SIZE_DESC=new FilePickerOrder(1,true,"大小 ↓"),MODIFIED=new FilePickerOrder(2,false,"修改时间 ↑"),MODIFIED_DESC=new FilePickerOrder(2,true,"修改时间 ↓"),TYPE=new FilePickerOrder(3,false,"类型 ↑");
    static final FilePickerOrder[] VALUES={NAME,NAME_DESC,TYPE,SIZE,SIZE_DESC,MODIFIED_DESC,MODIFIED};
    final String label;private final int key;private final boolean descending;
    private FilePickerOrder(int key,boolean descending,String label){this.key=key;this.descending=descending;this.label=label;}
    @Override public int compare(FilePickerDirectory.Entry left,FilePickerDirectory.Entry right){
        int kind=Boolean.compare(right.directory,left.directory);
        if(kind!=0)return kind;
        if(key==1||key==2){long a=key==1?left.size:left.modified,b=key==1?right.size:right.modified;
            if(a<0||b<0){int unknown=Boolean.compare(a<0,b<0);if(unknown!=0)return unknown;}
            int value=Long.compare(a,b);if(value!=0)return descending?-value:value;
        }
        if(key==3){int type=natural(left.type(),right.type());if(type!=0)return type;}
        int natural=natural(left.name(),right.name());
        if(natural!=0)return key==0&&descending?-natural:natural;
        int original=left.name().compareTo(right.name());
        return original!=0?original:left.path.toString().compareTo(right.path.toString());
    }
    private static boolean digit(char value){return Character.digit(value,10)>=0;}
    static int natural(String left,String right){
        int a=0,b=0;
        while(a<left.length()&&b<right.length()){
            char x=left.charAt(a),y=right.charAt(b);
            if(digit(x)&&digit(y)){
                int ae=a,be=b;
                while(ae<left.length()&&digit(left.charAt(ae)))ae++;
                while(be<right.length()&&digit(right.charAt(be)))be++;
                int az=a,bz=b;
                while(az<ae&&Character.digit(left.charAt(az),10)==0)az++;
                while(bz<be&&Character.digit(right.charAt(bz),10)==0)bz++;
                int length=Integer.compare(ae-az,be-bz);
                if(length!=0)return length;
                for(int i=0;i<ae-az;i++){
                    int n=Integer.compare(Character.digit(left.charAt(az+i),10),Character.digit(right.charAt(bz+i),10));
                    if(n!=0)return n;
                }
                int zeros=Integer.compare(ae-a,be-b);
                if(zeros!=0)return zeros;
                a=ae;b=be;
            }else{
                int order=Character.compare(Character.toLowerCase(x),Character.toLowerCase(y));
                if(order!=0)return order;
                a++;b++;
            }
        }
        return Integer.compare(left.length()-a,right.length()-b);
    }
}
