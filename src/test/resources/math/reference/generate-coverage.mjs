// 固定 JS 只运行于测试 oracle；不复制其 parser/layout 算法到生产。
// 用法：node src/test/resources/math/reference/generate-coverage.mjs [固定 bundle 路径]
import fs from 'node:fs/promises';
import path from 'node:path';
import vm from 'node:vm';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
const here=path.dirname(fileURLToPath(import.meta.url));
const root=path.resolve(here,'../../../../..');
const facts=JSON.parse(await fs.readFile(path.join(here,'katex-0.16.21-facts.json'),'utf8'));
const acceptance=JSON.parse(await fs.readFile(path.join(here,'acceptance-corpus.json'),'utf8'));
const matrix=JSON.parse(await fs.readFile(path.join(here,'support-matrix.json'),'utf8'));
const bundle=await fs.readFile(process.argv[2]||path.join(root,'build/math-complete/reference/katex/dist-katex.js'),'utf8');
if(crypto.createHash('sha256').update(bundle).digest('hex')!==facts.reference.bundleSha256)throw Error('固定 bundle hash 不符');
let calls=[],expansions=[],warnings=[],unsupported=[];
// 仅记录固定引擎实际消费；内部命令由合法 public 构建路径触达，绝不把其 token 注入 UILib。
const instrumented=bundle.replace('  callFunction(name, args, optArgs, token, breakOnTokenText) {',
    '  callFunction(name, args, optArgs, token, breakOnTokenText) { observeFunction(name);')
    .replace('  formatUnsupportedCmd(text) {','  formatUnsupportedCmd(text) { observeUnsupported(text);')
    .replace('    const expansion = typeof definition === "function" ? definition(this) : definition;',
        '    observeMacro(name); const expansion = typeof definition === "function" ? definition(this) : definition;');
if(instrumented===bundle)throw Error('固定观测点失效');
const context={module:{exports:{}},exports:{},observeFunction:n=>calls.push(n),observeMacro:n=>expansions.push(n),observeUnsupported:n=>unsupported.push(n),
    console:{warn:m=>warnings.push(String(m)),log:()=>{},error:()=>{}}};
vm.runInNewContext(instrumented,context,{filename:'coverage-katex-fixed.js',timeout:20000});
const katex=context.module.exports;
if(katex.version!=='0.16.21')throw Error('固定版本不符');
const B='\\';
const tex=String.raw;
const unique=xs=>[...new Set(xs)];
const count=xs=>xs.reduce((r,x)=>(r[x]=(r[x]||0)+1,r),{});
const esc=s=>String(s??'').replace(/\\/g,'\\\\').replace(/\t/g,'\\t').replace(/\n/g,'\\n').replace(/\r/g,'\\r');
const wrap=(mode,s)=>mode==='text'?tex`\text{${s}}`:s;
const rows=[],observations=[],unresolved=[];
function walk(n,out) {
    if(!n || typeof n!=='object')return;
    if(Array.isArray(n)){for(const c of n)walk(c,out);return;}
    if(n.type)out.types.push(n.type);
    if(n.type==='cr')out.lineBreakFacts.push({mode:n.mode,newLine:n.newLine,size:n.size??null});
    if(typeof n.text==='string')out.leaves.push({type:n.type,mode:n.mode,text:n.text,font:n.font??null,family:n.family??null});
    for(const [k,v] of Object.entries(n))if(k!=='loc' && v && typeof v==='object')walk(v,out);
}
const pluginSource=await fs.readFile(path.join(root,'build/math-complete/reference/vscode/fixed-plugin-index.js'),'utf8');
const pluginExports={};
vm.runInNewContext(pluginSource,{exports:pluginExports,require:name=>{if(name==='katex')return katex;throw Error('非固定插件依赖 '+name);},console:context.console},
    {filename:'coverage-fixed-plugin-1.1.2.js',timeout:20000});
function probe(source,display=true,settings={}) {
    calls=[];expansions=[];warnings=[];unsupported=[];
    let out=null,error=null,actualDisplayMode=null;
    const bridge={renderToString(input,options) {
        actualDisplayMode=options.displayMode;
        try {
            const tree=katex.__parse(input,{...options,macros:{}});out={types:[],leaves:[],lineBreakFacts:[]};walk(tree,out);
            return katex.renderToString(input,{...options,macros:{}});
        }catch(e){error=String(e);throw e;}
    }};
    const md={inline:{ruler:{after(){},before(){}}},block:{ruler:{after(){},before(){}}},core:{ruler:{push(){}}},renderer:{rules:{}}};
    const options={globalGroup:true,macros:{},trust:false,throwOnError:true,strict:'warn',...settings,katex:bridge};
    pluginExports.default(md,options);
    const rule=display?'math_block':'math_inline';
    md.renderer.rules[rule]([{content:source}],0,{}, {},{});
    return {outcome:error?(out?'reference-render-error':'error'):unsupported.length?'unsupported-output':'rendered',
        ...(error?{error}:{}),types:out?unique(out.types):[],leaves:out?out.leaves:[],
        ...(out?.lineBreakFacts.length?{lineBreakFacts:out.lineBreakFacts}:{}),
        calls:unique(calls),expansions:unique(expansions),warnings:unique(warnings),unsupported:unique(unsupported),
        oracleOptions:{requestedDisplayMode:display,actualDisplayMode,globalGroup:true,trust:false,strict:settings.strict||'warn'},
        pluginRule:rule,pluginSource:'build/math-complete/reference/vscode/fixed-plugin-index.js:431-458'};
}
function add(kind,mode,token,source,contextTag,options={}) {
    const id=kind+'-'+String(rows.length).padStart(4,'0');
    const oracle=probe(source,mode==='display',options.settings);
    const scope=options.scope||'default';
    // expected 只在 oracle 确认该字符是实际叶结点时启用。font 映射后的 Unicode styled 字形另记实际事实。
    let expected=oracle.outcome==='unsupported-output'?oracle.unsupported.join(''):options.expected||'';
    if(oracle.outcome!=='unsupported-output' && expected && !oracle.leaves.some(l=>l.text===expected || l.text===token))expected='';
    if(kind==='profile' && options.expected && oracle.leaves.map(l=>l.text).join('')===options.expected)expected=options.expected;
    const record={id,kind,mode,token,context:contextTag,scope,source,...oracle,expected};
    rows.push([id,kind,mode,token,contextTag,oracle.outcome,scope,source,oracle.types.join(','),expected]);
    observations.push(record);return record;
}
function envSource(name) {
    const args=['array','darray'].includes(name)?'{l|r}':name==='subarray'?'{c}':name.includes('alignat')?'{1}':'';
    const body=name==='CD'?tex`A @>f>> B \\ @VVgV @AAhA \\ C @= D`:
        name.startsWith('equation')?'a=b':name.startsWith('gather')||name==='subarray'?tex`a\\b`:tex`a&b\\c&d`;
    return tex`\begin{${name}}${args}${body}\end{${name}}`;
}
const fullCD=tex`\begin{CD}A @>f>g> B @<h<i< C \\ @VVjVk @AAlAm @| \\ D @= E @. F\end{CD}`;
function functionSource(f) {
    const n=f.name;
    if(n==='\\(' || n==='\\)')return [tex`\text{\(a\)}`,'text-math-delimiter'];
    if(n==='$')return [tex`\text{$a$}`,'text-math-dollar'];
    if(n==='\\]')return [tex`\text{\]}`,'mismatched-closing-default-error'];
    if(f.type==='environment')return [envSource('matrix'),'environment-open-close'];
    if(n==='\\hline'||n==='\\hdashline')return [tex`\begin{array}{lr}${n} a&b\\c&d\end{array}`,'array-row-rule'];
    if(['leftright','leftright-right','middle'].includes(f.type))return [tex`\left(a\middle|b\right)`,'leftright-paired'];
    if(f.type==='infix')return [tex`{a${n}${n==='\\above'?'{1pt}':' '}b}`,'infix'];
    if(n==='\\\\abovefrac')return [tex`{a\above{1pt}b}`,'parser-infix-rewrite'];
    if(n==='\\\\atopfrac')return [tex`{a\atop b}`,'parser-infix-rewrite'];
    if(n==='\\\\bracefrac')return [tex`{a\brace b}`,'parser-infix-rewrite'];
    if(n==='\\\\brackfrac')return [tex`{a\brack b}`,'parser-infix-rewrite'];
    if(n.startsWith('\\\\cd'))return [fullCD,'CD-constructed-handler'];
    if(n==='\\\\')return [tex`x\\[1pt]y`,'top-level-linebreak-handler'];
    if(n==='\\\\globallet')return [tex`\global\let\q=x\q`,'global-prefix-rewrite'];
    if(n==='\\\\globalfuture')return [tex`\global\futurelet\q xy\q`,'global-prefix-rewrite'];
    if(n==='\\\\globallong')return [tex`\global\long\def\q{x}\q`,'global-prefix-rewrite'];
    if(['\\def','\\gdef','\\edef','\\xdef'].includes(n))return [tex`${n}\q#1{#1^2}\q{x}`,'definition-consumed'];
    if(n==='\\global'||n==='\\long')return [tex`${n}\def\q{x}\q`,'definition-prefix-consumed'];
    if(n==='\\let')return [tex`\let\q=x\q`,'let-consumed'];
    if(n==='\\futurelet')return [tex`\futurelet\q xy\q`,'futurelet-consumed'];
    if(n==='\\relax')return [tex`x\relax+y`,'explicit-no-op'];
    if(n==='\\genfrac')return [tex`\genfrac{(}{)}{.4pt}{0}{a}{b}`,'genfrac-six-arguments'];
    if(f.type==='delimsizing')return [n+'(','fixed-delimiter'];
    if(f.type==='verb')return [tex`\verb|a b%|`,'verb-literal'];
    if(f.type==='cr')return [tex`x${n}[1pt]y`,'linebreak'];
    if(f.type==='cdlabel'||f.type==='cdlabelparent')return [fullCD,'CD-constructed-handler'];
    let s=n;
    for(let i=0;i<f.numArgs+f.numOptionalArgs;i++) {
        const optional=i<f.numOptionalArgs,type=f.argTypes?.[i]||'original';
        let value=type==='size'?'1pt':type==='color'?'red':type==='url'?'https://example.org':type==='raw'?'q':'x';
        if(f.type==='includegraphics'&&optional)value='width=1em';
        if(f.type==='html')value=i===0?(n==='\\htmlData'?'k=v':'q'):'x';
        if(f.type==='smash'&&optional)value='t';
        if(f.type==='textord')value='65';
        if(f.type==='sqrt'&&optional)value='3';
        if(f.type==='xArrow'&&optional)value='b';
        s+=(optional?'[':'{')+value+(optional?']':'}');
    }
    if(f.numArgs===0)s+=' x';
    return [f.allowedInMath?s:wrap('text',s),'arguments-by-fixed-registry:'+f.type];
}
function macroSource(m) {
    const n=m.name;
    const special={
        '\\@firstoftwo':tex`\@firstoftwo{x}{y}`, '\\@secondoftwo':tex`\@secondoftwo{x}{y}`,
        '\\@ifnextchar':tex`\@ifnextchar a{x}{y}a`, '\\@ifstar':tex`\@ifstar{x}{y}*`,
        '\\@hspace':tex`a\@hspace{1pt}b`, '\\@hspacer':tex`a\@hspacer{1pt}b`,
        '\\hspace':tex`a\hspace{1pt}b`,
        '\\tmspace':tex`a\tmspace+{3mu}{.1667em}b`,
        '\\bgroup':tex`\bgroup x\egroup`, '\\egroup':tex`\bgroup x\egroup`,
        '\\char':tex`\char"41`, '\\newcommand':tex`\newcommand{\q}[1]{#1^2}\q{x}`,
        '\\renewcommand':tex`\def\q{a}\renewcommand{\q}{b}\q`,
        '\\providecommand':tex`\providecommand{\q}{a}\q`,
        '\\noexpand':tex`\def\q{x}\edef\r{\noexpand\q}\r`,
        '\\expandafter':tex`\def\q{x}\expandafter\text\expandafter{\q}`,
        '\\TextOrMath':tex`\TextOrMath{T}{M}`,
        '\\message':tex`\message{coverage}x`, '\\errmessage':tex`\errmessage{coverage}x`, '\\show':tex`\show\alpha x`,
        '\\tag':tex`x\tag{A}`, '\\tag@literal':tex`x\tag@literal{A}`, '\\tag@paren':tex`x\tag@paren{A}`,
        '\\bra@ket':tex`\Braket{x|y||z}`, '\\bra@set':tex`\Set{x|y||z}`,
        '\\cr':tex`\begin{matrix}a\cr b\end{matrix}`,
        '\\not':tex`\not=`, '\\dots':tex`a\dots+b`, '\\dotsc':tex`a,\dotsc,b`,
        '\\dotso':tex`a\dotso b`, '\\cdots':tex`a\cdots+b`,
    };
    if(Object.hasOwn(special,n))return [special[n],'macro-context:'+m.kind];
    const stripped=(m.expansion||'').replace(/##/g,'');
    let args=0;while(stripped.includes('#'+(args+1)))args++;
    return [n+' '+Array.from({length:args},()=>'{x}').join('')+' y','macro-expansion:'+m.kind];
}
// 所有 symbol 身份都进入其实际 math/text 模式。保留宏遮蔽和模式切换的实际 oracle，不能用 registry 代替运行。
for(const s of facts.symbols) {
    let source=wrap(s.mode,s.name),tag='mode:'+s.mode;
    // Registry 中 active/grouping/script token 在合法结构中消费；字符的独立 glyph 另由显式 char 事实检查。
    if(s.name==='$') {source=tex`\text{$x$}`;tag='text-to-math-switch';}
    if(s.name==='{'||s.name==='}') {source=wrap(s.mode,'{x}');tag='group-boundary';}
    if(s.name==='^'||s.name==='_') {source='x'+s.name+'{2}';tag='script-context';}
    const command=facts.functions.find(f=>f.name===s.name && (s.mode==='text'?f.allowedInText:f.allowedInMath));
    if(command && command.numArgs>0){source=wrap(s.mode,s.name+'{x}');tag='symbol-through-function:'+command.type;}
    const p=add('symbol',s.mode,s.name,source,tag,{expected:s.replace??(!s.name.startsWith(B)?s.name:'')});
    p.catalog={font:s.font,group:s.group,replace:s.replace,origin:s.origin};
    if(p.outcome==='error')unresolved.push({id:p.id,kind:'symbol',token:s.name,mode:s.mode,error:p.error});
}
for(const f of facts.functions) {
    const [source,tag]=functionSource(f),p=add('function','display',f.name,source,tag);
    p.registryType=f.type;p.origin=f.origin;
    p.constructionPath=f.type==='verb'?'Lexer→Parser.parseSymbol:dist18661-18684':'Parser.callFunction';
    p.constructedIdentity=(f.type==='verb'&&p.types.includes('verb')) || p.calls.includes(f.name) || ['\\right','\\middle','\\)','\\]','$','\\hline','\\hdashline'].includes(f.name);
    if(p.outcome==='error' && f.name!=='\\]')unresolved.push({id:p.id,kind:'function',token:f.name,error:p.error});
}
for(const e of facts.environments)for(const mode of ['math','display']) {
    const p=add('environment',mode,e.name,envSource(e.name),'environment-mode:'+mode);p.origin=e.origin;
}
for(const m of facts.macros) {
    const [source,tag]=macroSource(m),p=add('macro','display',m.name,source,tag);p.macroKind=m.kind;p.origin=m.origin;
    p.constructedIdentity=p.expansions.includes(m.name);
    if(p.outcome==='error')unresolved.push({id:p.id,kind:'macro',token:m.name,error:p.error});
}
// Unicode text、真实字族成员和显式候选覆盖另有视觉身份，不以 font alias 名字存在算 PASS。
for(const family of ['textrm','textsf','texttt'])for(const member of ['normal','bold','italic','bolditalic']) {
    const raw=B+family+'{AΩ中}';
    const source=member==='normal'?raw:member==='bold'?tex`\textbf{${raw}}`:member==='italic'?tex`\textit{${raw}}`:tex`\textbf{\textit{${raw}}}`;
    add('profile','display',family+':'+member,source,'unicode-text-real-family:'+member,{expected:'AΩ中'});
}
add('profile','display','explicit-candidates',tex`\textsf{A}`,'explicit-candidates:math-source',{expected:'A'});
for(const source of [tex`\sum_{i=1}^n+\verb|\begin{align}|`,tex`\sum_{i=1}^n+\verb|\begin{align*}|`,
        tex`\sum_{i=1}^n+\verb|\BEGIN{CD}|`,tex`\sum_{i=1}^n+\verb|\begin{aligned}|`])
    add('plugin-context','math','whole-source-auto-display',source,'fixed-plugin-source-qualification');
for(const token of facts.implicit)add('implicit','display',token,
    token==='^'||token==='_'?'x'+token+'{2}':tex`\sum${token}_{i=1}^n`,'implicit-script-or-limit');
for(const c of acceptance.cases) {
    if(!c.defaultSettings || !c.tex)continue; // strict:error、跨公式序列不伪装成单公式默认设置。
    const p=add('acceptance',c.displayMode?'display':'math',c.id,c.tex,'fixed-acceptance:'+c.caseId);
    p.originalOutcome=c.outcome;
    if((c.result==='error')!==(p.outcome==='error')) {
        if(p.oracleOptions.actualDisplayMode===c.displayMode)throw Error('原始 direct-KaTeX fact 结果漂移: '+c.caseId);
        p.directKatexFact=c.outcome;p.pluginContextOverride='fixed-plugin katexInline 全公式 auto-display';
    }
}
// 精确源范围和整式保留的必要反例，均由固定引擎实际拒绝。
for(const source of [tex`\frac{x}`,tex`\left(x`,tex`x^a^b`,tex`\begin{matrix}a\end{pmatrix}`,
    tex`\rule{2zz}{1pt}`,tex`\genfrac{}{}{1pt}{9}{a}{b}`,tex`\text{\alpha}`,tex`𝑨+\notACommand{x}`])
    add('negative','display','invalid',source,'default-reference-error');
const identities=(kind,mode,token)=>observations.filter(x=>x.kind===kind&&x.token===token&&(mode===null||x.mode===mode));
for(const entry of matrix.entries) {
    const matches=identities(entry.kind,entry.mode,entry.token);
    entry.coverageReference=matches.map(c=>({caseId:c.id,mode:c.mode,scope:c.scope,context:c.context,outcome:c.outcome,
        astTypes:c.types,functionCalls:c.calls,macroExpansions:c.expansions,constructedIdentity:c.constructedIdentity??null,constructionPath:c.constructionPath??null,
        ...(c.lineBreakFacts?{lineBreakFacts:c.lineBreakFacts}:{}),
        oracleOptions:c.oracleOptions,pluginRule:c.pluginRule,pluginSource:c.pluginSource,expectedGlyph:c.expected,catalog:c.catalog??null,error:c.error??null}));
    entry.referenceDefault=matches.length?'coverage-oracle-recorded':entry.referenceDefault;
    entry.coverageExecution={semantic:'pending-parent-run',layout:'pending-parent-run',gpu:'pending-parent-run-and-image-review'};
}
matrix.coverage={reference:facts.reference,corpus:'coverage-corpus.tsv',generator:'generate-coverage.mjs',
    status:'reference-generated-runtime-pending',caseCount:rows.length,byKind:count(observations.map(x=>x.kind)),
    unresolvedReferenceContexts:unresolved,
    referenceOnlyFacts:acceptance.cases.filter(c=>!c.defaultSettings||!c.tex).map(c=>({caseId:c.caseId,settings:c.settings,
        reason:!c.defaultSettings?'nondefault strict:error；不适用于默认生产入口':'显式跨公式宏上下文；由 MathMacroScenario 独立验证'})),
    rule:'没有父正式运行 PASS 和逐页亲读 PNG，不升级 semantic/layout/gpu；宏展开成功只记录 reference target family。'};
await fs.writeFile(path.join(here,'coverage-corpus.tsv'),'# id kind mode token context outcome scope source referenceTypes expectedGlyph\n'+rows.map(r=>r.map(esc).join('\t')).join('\n')+'\n');
await fs.writeFile(path.join(here,'support-matrix.json'),JSON.stringify(matrix,null,2)+'\n');
const out=path.join(root,'build/math-complete');await fs.mkdir(out,{recursive:true});
await fs.writeFile(path.join(out,'coverage-reference.json'),JSON.stringify({reference:facts.reference,observations,unresolved},null,2)+'\n');
console.log(JSON.stringify({cases:rows.length,byKind:count(observations.map(x=>x.kind)),outcomes:count(observations.map(x=>x.outcome)),unresolved},null,2));
