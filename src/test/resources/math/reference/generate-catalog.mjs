// 只转换固定静态 facts；不加载或执行 KaTeX，不复制解析/布局算法。
// DSH 调用方以 tools.read 分页读 JSON（下一页实际 last line + 1），再逐文件 tools.write。
export function escapeField(value) {
    if (value === null || value === undefined) return '\\N';
    return String(value).replace(/\\/g, '\\\\').replace(/\t/g, '\\t')
        .replace(/\n/g, '\\n').replace(/\r/g, '\\r');
}
export function unescapeField(field) {
    if (field === '\\N') return null;
    return field.replace(/\\([\\tnr])/g, (_, c) => ({'\\':'\\', t:'\t', n:'\n', r:'\r'})[c]);
}
function table(header, rows) {
    return '# ' + header + '\n' + rows.map(row => row.map(escapeField).join('\t')).join('\n') + '\n';
}
export function generate(facts) {
    const {reference, functions, environments, macros, symbols, implicit} = facts;
    if (reference.katex !== '0.16.21' || reference.commit !== '923f2aa15692d06ef77051bfb018c17339ef1ab4')
        throw Error('固定参照不符');
    const source = x => [x.origin.path, x.origin.distLine, x.origin.url];
    const argumentTypes = x => [x.argTypes !== undefined, (x.argTypes || []).length, ...(x.argTypes || [])];
    const kinds = xs => xs.reduce((r,x) => (r[x]=(r[x]||0)+1,r),{});
    const counts = {functions:functions.length,environments:environments.length,macros:macros.length,
        symbols:symbols.length,macroKinds:kinds(macros.map(x=>x.kind)),symbolsByMode:kinds(symbols.map(x=>x.mode))};
    for (const key of ['functions','environments','macros','symbols']) {
        if (counts[key] !== facts.statistics[key]) throw Error('参照统计不符: '+key);
        const seen = new Set();
        for (const row of facts[key]) {
            const id = JSON.stringify([row.mode,row.name]);
            if (seen.has(id)) throw Error('重复事实: '+id);
            seen.add(id);
            if (!row.origin?.path || !row.origin?.url) throw Error('缺少来源: '+id);
        }
    }
    for (const m of macros) {
        if (m.kind === 'string' && typeof m.expansion !== 'string') throw Error('字符串宏不完整: '+m.name);
        if (m.kind === 'function' && m.expansion !== undefined) throw Error('函数宏不可伪造expansion: '+m.name);
        if (!['string','function'].includes(m.kind)) throw Error('未知宏kind: '+m.kind);
    }
    const resources = {
        'symbols.tsv':table('mode token font group replace visibility sourcePath distLine sourceUrl',
            symbols.map(s=>[s.mode,s.name,s.font,s.group,s.replace,s.visibility,...source(s)])),
        'functions.tsv':table('name type numArgs numOptionalArgs allowedInArgument allowedInText allowedInMath infix primitive visibility sourcePath distLine sourceUrl argTypesSpecified argTypeCount argTypes...',
            functions.map(f=>[f.name,f.type,f.numArgs,f.numOptionalArgs,f.allowedInArgument,f.allowedInText,f.allowedInMath,f.infix,f.primitive,f.visibility,...source(f),...argumentTypes(f)])),
        'environments.tsv':table('name type numArgs numOptionalArgs allowedInText sourcePath distLine sourceUrl argTypesSpecified argTypeCount argTypes...',
            environments.map(e=>[e.name,e.type,e.numArgs,e.numOptionalArgs,e.allowedInText,...source(e),...argumentTypes(e)])),
        'macros.tsv':table('name kind expansion visibility sourcePath distLine sourceUrl',
            macros.map(m=>[m.name,m.kind,m.expansion,m.visibility,...source(m)])),
        'implicit.tsv':table('token',implicit.map(x=>[x])),
        'reference.tsv':table('key value',[
            ...Object.entries(reference), ...Object.entries(facts.defaults).filter(([k])=>k!=='sources'&&k!=='macros').map(([k,v])=>['default.'+k,v])])
    };
    // 往返验算涵盖所有字段；null、字面反斜杠N、Tab、换行和代理对保持不同。
    for (const text of Object.values(resources)) for (const line of text.split('\n').slice(1).filter(Boolean)) {
        for (const field of line.split('\t')) if (escapeField(unescapeField(field)) !== field)
            throw Error('TSV escape往返失败');
    }
    const commands = new Set([...functions,...macros,...symbols].map(x=>x.name).concat(implicit).filter(x=>x.startsWith('\\')));
    if (commands.size !== facts.statistics.uniqueCommandTokens) throw Error('命令token统计不符');
    const cases = facts.probes.map(p => ({...p,
        caseId:p.id + (p.displayMode === undefined ? '' : p.displayMode ? ':display' : ':inline'),
        ...(p.id === 'macro-cross-formula' ? {sequence:['\\def\\persist{a}','\\persist'],sharedMacros:true} : {})}));
    const corpus = {reference,defaults:facts.defaults,scope:'固定参照引擎输入，displayMode显式；不是插件Markdown定界识别测试',
        status:{semantic:'unverified',layout:'unverified',gpu:'unverified'},cases};
    const evidence = (kind, token) => cases.filter(p => p.defaultSettings &&
        (kind === 'environment' ? p.id === 'environment:'+token :
            ['function','macro'].includes(kind) && p.tex?.match(/^\\(?:[A-Za-z@]+|[^A-Za-z])/u)?.[0] === token))
        .map(p=>({caseId:p.caseId,outcome:p.outcome,displayMode:p.displayMode}));
    const matrix = {reference,defaults:facts.defaults,counts:{...counts,uniqueCommandTokens:commands.size},
        baseline:facts.baseline,productionStatus:{catalogue:'registered',semantic:'unverified',layout:'unverified',gpu:'unverified'},
        note:'baseline只记录旧MathContent名字分支；每项默认行为以corpus具体输入为证，其余unverified。登记不代表生产解析、布局或视觉支持。',
        entries:[...symbols.map(s=>['symbol',s.mode,s.name]),...functions.map(f=>['function',null,f.name]),
            ...environments.map(e=>['environment',null,e.name]),...macros.map(m=>['macro',null,m.name]),...implicit.map(x=>['implicit',null,x])]
            .map(([kind,mode,token])=>{const referenceEvidence=evidence(kind,token);
                return {kind,mode,token,referenceDefault:referenceEvidence.length?'examples-recorded':'unverified',
                    referenceEvidence,semantic:'unverified',layout:'unverified',gpu:'unverified'};})};
    return {resources,corpus,matrix,counts};
}
