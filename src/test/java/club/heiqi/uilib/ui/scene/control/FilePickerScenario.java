package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 用真实目录与编辑提交防住混排、跨页漏项及旧确认覆盖新草稿。 */
public final class FilePickerScenario extends TestApplication {
    public static void main(String[] args)throws Exception{new FilePickerScenario().run(args);}
    @Override protected void runTests()throws Exception{
        scenario("目录优先自然名称及超长数字段",()->{
            List<FilePickerDirectory.Entry> entries=new ArrayList<>();
            for(String name:Arrays.asList("片10","片02","片2","片1","目录10","目录2"))entries.add(new FilePickerDirectory.Entry(Paths.get(name),name.startsWith("目录")));
            entries.sort(FilePickerOrder.NAME);
            require(entries.stream().map(FilePickerDirectory.Entry::name).collect(Collectors.toList()).equals(Arrays.asList("目录2","目录10","片1","片2","片02","片10")),"目录优先或自然数字顺序错误");
            require(FilePickerOrder.natural("片999999999999999999999999","片1000000000000000000000000")<0,"数字段溢出或按字典序比较");
            require(FilePickerOrder.natural("VIDEO2","video10")<0,"大小写改变自然名称语义");
        });
        scenario("真实全目录有序前后页与定位刷新",()->{
            Path root=Files.createTempDirectory("uilib 有序目录 +");
            try(FilePickerDirectory reader=new FilePickerDirectory()){
                for(int i=79;i>=0;i--){Files.createFile(root.resolve("视频 "+i+".ViDeO"));Files.createDirectory(root.resolve("目录 "+i));}
                List<String> expected=new ArrayList<>();for(int i=0;i<80;i++)expected.add("目录 "+i);for(int i=0;i<80;i++)expected.add("视频 "+i+".ViDeO");
                long request=1;reader.browse(request,root,FilePickerQuery.first());FilePickerDirectory.Result result=await(reader);
                List<String> actual=new ArrayList<>();
                while(true){
                    require(result.error==null&&result.entries.size()<=FilePickerDirectory.PAGE_SIZE,"真实目录读取失败或页预算无界");
                    for(FilePickerDirectory.Entry e:result.entries)actual.add(e.name());
                    if(!result.next)break;
                    reader.browse(++request,root,FilePickerQuery.after(result.page+1,result.entries.get(result.entries.size()-1)));result=await(reader);
                }
                require(actual.equals(expected),"跨页顺序不一致、漏项或重复："+actual);
                while(result.previous){reader.browse(++request,root,FilePickerQuery.before(result.page-1,result.entries.get(0)));result=await(reader);}
                require(result.entries.stream().map(FilePickerDirectory.Entry::name).collect(Collectors.toList()).equals(expected.subList(0,FilePickerDirectory.PAGE_SIZE)),"返回第一页没有恢复同一有序内容");
                Path chosen=root.resolve("视频 70.ViDeO");reader.locate(++request,chosen);result=await(reader);
                require(chosen.equals(result.selected)&&result.entries.stream().anyMatch(e->e.path.equals(chosen)),"直接定位文件没有显露当前目标");
                Files.createDirectory(root.resolve("目录 -1"));reader.browse(++request,root,FilePickerQuery.first());result=await(reader);
                require(result.entries.get(0).name().equals("目录 -1"),"刷新沿用了过期目录顺序");
                System.out.println("FILE_PICKER_ORDER actualDirectory=true total="+actual.size()+" globalDirectoryFirst=true natural=true backward=true locate=true refresh=true pageBudget="+FilePickerDirectory.PAGE_SIZE);
            }finally{deleteTree(root);}
        });
        scenario("全目录名称查询、扩展名筛选与元数据顺序",()->{
            Path root=Files.createTempDirectory("uilib 全局搜索");
            try(FilePickerDirectory reader=new FilePickerDirectory()){
                for(int i=0;i<140;i++)Files.write(root.resolve("font "+i+".ttf"),new byte[i]);
                Files.write(root.resolve("font 200.otf"),new byte[200]);Files.createFile(root.resolve("other.any"));Files.createDirectory(root.resolve("font folder"));
                reader.browse(1,root,FilePickerQuery.first().filter("font 13","").order(FilePickerOrder.SIZE_DESC));FilePickerDirectory.Result result=await(reader);
                require(result.entries.size()==11&&result.entries.get(0).name().equals("font 139.ttf")&&result.entries.get(result.entries.size()-1).name().equals("font 13.ttf"),"查询只覆盖可见页或大小排序失效");
                reader.browse(2,root,FilePickerQuery.first().filter("font","otf").order(FilePickerOrder.NAME));result=await(reader);
                require(result.entries.size()==2&&result.entries.get(0).directory&&result.entries.get(1).size==200,"扩展名过滤隐藏目录或元数据未在worker取得");
                reader.browse(3,root,FilePickerQuery.first());result=await(reader);require(result.next,"恢复所有文件丢失分页");
            }finally{deleteTree(root);}
        });
        scenario("前后历史保留目录、选择与滚动位置",()->{
            Path root=Files.createTempDirectory("uilib 导航历史");Path child=Files.createDirectory(root.resolve("child")),file=Files.createFile(root.resolve("selected.any"));
            ReactiveDomain domain=ReactiveDomain.direct();FilePickerSelection[] state={null};
            try{
                domain.execute(()->{state[0]=new FilePickerSelection(domain,root,p->{},()->{});state[0].attach();});settle(domain,state[0]);
                domain.execute(()->{state[0].activate(new FilePickerDirectory.Entry(file,false));state[0].scrollPosition=()->123;state[0].navigate(child);});settle(domain,state[0]);
                domain.execute(()->{require(state[0].backCount.get()==1,"进入目录没有前历史");state[0].history(-1);});settle(domain,state[0]);
                domain.execute(()->{require(state[0].directory.get().equals(root)&&file.equals(state[0].currentSelection())&&state[0].scroll.get()==123,"返回没有恢复位置或选择");state[0].history(1);});settle(domain,state[0]);
                domain.execute(()->require(state[0].directory.get().equals(child),"前进没有恢复目录"));
            }finally{domain.execute(()->{if(state[0]!=null)state[0].detach();});deleteTree(root);}
        });
        scenario("直接定位后页文件再导航返回保留有序边界",()->{
            Path root=Files.createTempDirectory("uilib 后页定位");Path child=Files.createDirectory(root.resolve("child"));
            for(int i=0;i<150;i++)Files.createFile(root.resolve("file "+i+".any"));Path chosen=root.resolve("file 149.any");
            ReactiveDomain domain=ReactiveDomain.direct();FilePickerSelection[] state={null};
            try{
                domain.execute(()->{state[0]=new FilePickerSelection(domain,root,p->{},()->{});state[0].attach();});settle(domain,state[0]);
                domain.execute(()->{state[0].path.reset(chosen.toString());state[0].go(state[0].path.request(true));});settle(domain,state[0]);
                domain.execute(()->{require(chosen.equals(state[0].currentSelection())&&state[0].page.get()>0,"反例没有定位到后页文件");state[0].navigate(child);});settle(domain,state[0]);
                domain.execute(()->state[0].history(-1));settle(domain,state[0]);
                domain.execute(()->require(chosen.equals(state[0].currentSelection())&&state[0].entries.get().stream().anyMatch(e->chosen.equals(e.path)),"返回后页定位丢失原边界/文件选择"));
            }finally{domain.execute(()->{if(state[0]!=null)state[0].detach();});deleteTree(root);}
        });
        scenario("路径草稿变更后旧核验不提交与选择提示一致",()->{
            Path root=Files.createTempDirectory("uilib 选择目标 +");Path a=Files.createFile(root.resolve("A.any")),b=Files.createFile(root.resolve("B.any"));
            ReactiveDomain domain=ReactiveDomain.direct();FilePickerSelection[] state={null};List<Path> chosen=new ArrayList<>();
            try{
                domain.execute(()->{state[0]=new FilePickerSelection(domain,root,chosen::add,()->{});state[0].attach();});settle(domain,state[0]);
                domain.execute(()->{
                    state[0].activate(new FilePickerDirectory.Entry(a,false));state[0].confirm();
                    state[0].path.select(0,state[0].path.snapshot().get().text.length());state[0].path.replaceSelection(b.toString());
                });
                settle(domain,state[0]);
                domain.execute(()->{
                    require(chosen.isEmpty()&&state[0].showing.get(),"旧A核验提交并关闭了新草稿");
                    require(state[0].path.snapshot().get().text.equals(b.toString()),"旧核验覆盖了B路径");
                    require(state[0].currentSelection()==null&&state[0].targetLabel().contains("B.any"),"选择提示仍冒充旧A");
                    state[0].confirm();
                });settle(domain,state[0]);
                require(chosen.equals(Collections.singletonList(b)),"当前确认目标不是B或重复回调");
                System.out.println("FILE_PICKER_CONFIRM staleVerifyIgnored=true currentDraftPreserved=true exactTarget=true callbackOnce=true");
            }finally{domain.execute(()->{if(state[0]!=null)state[0].detach();});deleteTree(root);}
        });
    }
    private static FilePickerDirectory.Result await(FilePickerDirectory reader)throws Exception{
        long deadline=System.nanoTime()+5_000_000_000L;FilePickerDirectory.Result result;
        while((result=reader.take())==null){if(System.nanoTime()>deadline)throw new AssertionError("真实目录读取没有完成");Thread.sleep(2);}
        return result;
    }
    private static void settle(ReactiveDomain domain,FilePickerSelection selection)throws Exception{
        long deadline=System.nanoTime()+5_000_000_000L;boolean[] busy={true};
        do{domain.execute(()->{selection.receiveCompletion();busy[0]=selection.busy.get();});if(busy[0])Thread.sleep(2);}
        while(busy[0]&&System.nanoTime()<deadline);
        require(!busy[0],"真实文件选择状态没有完成");
    }
    private static void deleteTree(Path root)throws Exception{
        try(Stream<Path> paths=Files.walk(root)){for(Path path:paths.sorted(Collections.reverseOrder()).collect(Collectors.toList()))Files.deleteIfExists(path);}
    }
}
