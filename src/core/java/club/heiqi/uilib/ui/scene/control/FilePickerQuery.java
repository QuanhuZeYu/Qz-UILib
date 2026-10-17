package club.heiqi.uilib.ui.scene.control;

import java.util.Locale;
/** 一次有序页的不可变查询；扫描全目录，仅保留一页候选，不累计目录快照。 */
final class FilePickerQuery {
    enum Direction { FIRST, NEXT, PREVIOUS, AROUND }
    final long page;
    final Direction direction;
    final FilePickerDirectory.Entry boundary;
    final String text,extensions;
    final FilePickerOrder order;
    private FilePickerQuery(long page,Direction direction,FilePickerDirectory.Entry boundary,String text,String extensions,FilePickerOrder order){
        this.page=page;this.direction=direction;this.boundary=boundary;this.text=text.trim().toLowerCase(Locale.ROOT);this.extensions=extensions.toLowerCase(Locale.ROOT);this.order=order;
    }
    static FilePickerQuery first(){return new FilePickerQuery(0,Direction.FIRST,null,"","",FilePickerOrder.NAME);}
    static FilePickerQuery after(long page,FilePickerDirectory.Entry boundary){return new FilePickerQuery(page,Direction.NEXT,boundary,"","",FilePickerOrder.NAME);}
    static FilePickerQuery before(long page,FilePickerDirectory.Entry boundary){return new FilePickerQuery(Math.max(0,page),Direction.PREVIOUS,boundary,"","",FilePickerOrder.NAME);}
    static FilePickerQuery around(FilePickerDirectory.Entry entry){return new FilePickerQuery(0,Direction.AROUND,entry,"","",FilePickerOrder.NAME);}
    FilePickerQuery filter(String text,String extensions){return new FilePickerQuery(page,direction,boundary,text,extensions,order);}
    FilePickerQuery order(FilePickerOrder order){return new FilePickerQuery(page,direction,boundary,text,extensions,order);}
    boolean accepts(FilePickerDirectory.Entry entry){
        if(!entry.name().toLowerCase(Locale.ROOT).contains(text))return false;
        if(entry.directory||extensions.isEmpty())return true;
        for(String ext:extensions.split(","))if(entry.type().equals(ext.trim().replaceFirst("^[.]","")))return true;
        return false;
    }
}
