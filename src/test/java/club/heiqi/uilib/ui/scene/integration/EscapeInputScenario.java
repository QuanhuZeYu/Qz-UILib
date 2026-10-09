package club.heiqi.uilib.ui.scene.integration;
import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.scene.host.SceneFramePipeline;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.host.SceneEscapeInputAdapter;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.layout.Size;

public final class EscapeInputScenario extends TestApplication {
    private final class Page {
        final ReactiveDomain domain; final Runnable drain;
        final SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
        ScenePageSession session;
        ScenePageContext context;
        SceneRuntime runtime; SceneFramePipeline pipe; SceneEscapeInputAdapter keys;
        SceneNode a,m,n; Signal<Boolean> outer,inner; Signal<BooleanSupplier> policy, innerAction;
        Signal<Runnable> click; int fallback, pageCalls, outerCalls, innerCalls;
        Page() { this(ReactiveDomain.direct(),()->{}); }
        Page(ReactiveDomain domain,Runnable drain) {
            this.domain=domain; this.drain=drain;
            session=ScenePageSession.open(domain,host,host,ctx->{
                context=ctx;
                runtime=ctx.runtime();
                outer=domain.signal(false);inner=domain.signal(false);policy=domain.signal(()->{pageCalls++;return false;});
                innerAction=domain.signal(()->{innerCalls++;inner.set(false);return true;});
                click=domain.signal(()->{outer.set(true);inner.set(true);});
                a=new SceneNode().text("A").focusable(true).size(Size.fixed(20),Size.fixed(20)).bind(NodeBinding.value(click,SceneNode::onClick));
                m=new SceneNode().text("M").focusable(true);n=new SceneNode().text("N").focusable(true);
                SceneNode inside=new SceneNode().modal(true).initialFocus(n).add(n).bind(NodeBinding.value(inner,SceneNode::visible)).bind(NodeBinding.value(innerAction,SceneNode::onEscape));
                SceneNode outside=new SceneNode().modal(true).initialFocus(m).add(m).add(inside).bind(NodeBinding.value(outer,SceneNode::visible))
                    .onEscape(()->{outerCalls++;outer.set(false);inner.set(false);return true;});
                ctx.mount(new SceneNode().initialFocus(a).add(a).add(outside).bind(NodeBinding.value(policy,SceneNode::onEscape)));
                keys=new SceneEscapeInputAdapter(()->session.escape(()->fallback++));keys.availability(true);keys.historyDrained(false);
            });drain.run();
            pipe=session.pipeline();
            onClose(()->{session.close();drain.run();});
            frame();
        }
        void frame(){session.frame();drain.run();}
        void focus(SceneNode target){domain.execute(()->require(runtime.focusRouter().focused().get()==target,"实际焦点错误"));drain.run();}
        void escape(){session.escape(()->fallback++);}
    }
    @Override protected void runTests() throws Exception {
        scenario("嵌套消费只退出当前层并恢复原焦点",()->{
            Page p=new Page();p.session.pointer(ScenePointerEvent.syntheticActivation(1,1));p.frame();p.focus(p.n);
            p.escape();p.frame();p.focus(p.m);require(p.innerCalls==1&&p.outerCalls==0&&p.pageCalls==0&&p.fallback==0,"消费不能传播");
            p.escape();p.frame();p.focus(p.a);require(p.outerCalls==1&&p.pageCalls==0,"外层消费停止页面关闭");
            p.escape();p.frame();require(p.pageCalls==1&&p.fallback==1,"未消费才宿主fallback");
        });
        scenario("未消费模态直接页面策略不调用后台外层",()->{
            Page p=new Page();p.domain.execute(()->{p.outer.set(true);p.inner.set(true);p.innerAction.set(()->{p.innerCalls++;return false;});p.policy.set(()->{p.pageCalls++;return true;});});
            p.escape();p.frame();p.focus(p.n);require(p.innerCalls==1&&p.outerCalls==0&&p.pageCalls==1&&p.fallback==0,"只当前范围后页面策略");
        });
        scenario("指针导航ESC混合输入保留结算顺序",()->{
            Page p=new Page();p.session.pointer(ScenePointerEvent.syntheticActivation(1,1));p.session.navigation(SceneNavigationEvent.NEXT);p.escape();p.frame();p.focus(p.m);
            require(p.innerCalls==1&&p.outerCalls==0&&p.fallback==0,"先前click显露内层应先结算");
        });
        scenario("一段ESC只退出一层失焦尾部不关闭",()->{
            Page p=new Page();p.session.pointer(ScenePointerEvent.syntheticActivation(1,1));p.frame();p.keys.key(true,false);p.keys.key(true,true);p.keys.key(true,false);p.frame();p.focus(p.m);
            p.keys.availability(false);p.keys.availability(true);p.keys.key(false,false);p.keys.key(true,false);p.keys.historyDrained(true);p.frame();p.focus(p.m);
            p.keys.historyDrained(false);p.keys.key(true,true);p.frame();p.focus(p.m);
            p.keys.key(true,false);p.frame();p.focus(p.a);require(p.fallback==0,"repeat不能级联关页");
        });
        scenario("Effect失败不能运行fallback且保原异常",()->{
            Page p=new Page();RuntimeException failure=new RuntimeException("ESC Effect失败");
            p.domain.execute(()->{Signal<Boolean> changed=p.domain.signal(false);Owner owner=p.context.scope();
                p.domain.effect(owner,()->{if(changed.get())throw failure;});p.policy.set(()->{changed.set(true);return false;});});
            p.escape();try{p.frame();throw new AssertionError("应失败");}catch(RuntimeException actual){require(actual==failure,"原异常");}
            require(p.fallback==0,"失败不能fallback");
        });
        // 底层合同：只关闭pipeline，验证其结算token失效；页面终止另由session覆盖。
        scenario("排队域Effect关闭管线拦迟到fallback和后续ESC",()->{
            ArrayDeque<Runnable> queue=new ArrayDeque<>();Runnable drain=()->{while(!queue.isEmpty())queue.remove().run();};Page p=new Page(new ReactiveDomain(queue::add),drain);
            p.domain.execute(()->{Signal<Boolean> closing=p.domain.signal(false);Owner owner=p.context.scope();
                p.domain.effect(owner,()->{if(closing.get())p.pipe.close();});p.policy.set(()->{p.pageCalls++;closing.set(true);return false;});});drain.run();
            p.escape();p.escape();p.session.frame();drain.run();require(p.pageCalls==1&&p.fallback==0,"关闭后迟到结果与后续意图不执行");
        });
        scenario("fallback观察已经完成的Effect而不是handler中间态",()->{
            Page p=new Page();final int[] observed={0};
            p.domain.execute(()->{Signal<Boolean> changed=p.domain.signal(false);Owner owner=p.context.scope();
                p.domain.effect(owner,()->{if(changed.get())observed[0]=17;});p.policy.set(()->{changed.set(true);return false;});});
            p.session.escape(()->{require(observed[0]==17,"fallback早于Effect结算");p.fallback++;});p.frame();require(p.fallback==1,"fallback应执行一次");
        });
        scenario("域内frame仍等外层结算后递交fallback",()->{
            Page p=new Page();final int[] observed={0};
            p.domain.execute(()->{Signal<Boolean> changed=p.domain.signal(false);Owner owner=p.context.scope();
                p.domain.effect(owner,()->{if(changed.get())observed[0]=17;});p.policy.set(()->{changed.set(true);return false;});});
            p.session.escape(()->{require(observed[0]==17,"嵌套frame早于Effect结算");p.fallback++;});
            p.domain.execute(p.session::frame);require(p.fallback==1,"域内frame回调丢失");
        });
        scenario("同域跨frame先ESC后click不得倒序",()->{
            Page p=new Page();p.domain.execute(()->{p.escape();p.session.frame();p.session.pointer(ScenePointerEvent.syntheticActivation(1,1));p.session.frame();});
            p.focus(p.n);require(p.fallback==1&&p.innerCalls==0,"后帧click不能先显露modal给前帧ESC消费");
        });
        // 底层合同：runtime单独close也必须使既有管线结果失效。
        scenario("runtime单独关闭不能迟到fallback或replay",()->{
            Page p=new Page();p.domain.execute(()->{Signal<Boolean> closing=p.domain.signal(false);Owner owner=p.context.scope();
                p.domain.effect(owner,()->{if(closing.get())p.runtime.close();});p.policy.set(()->{closing.set(true);return false;});});
            p.escape();p.frame();require(p.fallback==0,"runtime已关闭不得回调");
        });
        scenario("正式handler不得改树且只读拒绝关闭命令",()->{
            Page p=new Page();p.domain.execute(()->{try{p.domain.readOnly(()->p.runtime.dispatchEscape());throw new AssertionError("只读应拒绝");}catch(IllegalStateException expected){}
                p.policy.set(()->{try{p.runtime.captureMount(p.a).detach();throw new AssertionError("handler不得改树");}catch(IllegalStateException expected){}return true;});});
            p.escape();p.frame();p.focus(p.a);require(p.fallback==0,"消费保持");
        });
    }
    public static void main(String[] args)throws Exception{new EscapeInputScenario().run(args);}
}
