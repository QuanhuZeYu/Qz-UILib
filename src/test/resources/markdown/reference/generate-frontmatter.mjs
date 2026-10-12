import fs from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
import { createHash } from 'node:crypto';
import { stripTypeScriptTypes, createRequire } from 'node:module';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

// 固定扩展和锁依赖实际运行，只替代设置读取与本地化，不由生产代码生成期望。
const root = new URL('../../../../../', import.meta.url);
const cache = new URL('build/frontmatter-oracle/', root);
const output = new URL('./frontmatter-corpus.json', import.meta.url);
const corpus = JSON.parse(await fs.readFile(output, 'utf8'));
const commit = corpus.reference.commit;
const prefix = 'https://raw.githubusercontent.com/microsoft/vscode/' + commit + '/extensions/markdown-language-features/';
const sources = [
 ['yamlPreamble.ts', prefix + 'src/extensions/yamlPreamble/yamlPreamble.ts'],
 ['dom.ts', prefix + 'src/util/dom.ts'],
 ['package-lock.json', prefix + 'package-lock.json'],
 ['markdown-it.cjs', 'https://unpkg.com/markdown-it@14.2.0/dist/markdown-it.js'],
];
await fs.mkdir(cache, {recursive:true});
const texts = {};
for (const [name, url] of sources) {
 const response = await fetch(url);
 if(!response.ok)throw new Error(url + ': HTTP ' + response.status);
 const text = await response.text();
 if(createHash('sha256').update(text).digest('hex') !== corpus.reference.hashes[name])throw new Error('固定模块字节不匹配：'+name);
 texts[name]=text;
 await fs.writeFile(new URL(name,cache),text);
}
const lock=JSON.parse(texts['package-lock.json']);
const dependency=lock.packages['node_modules/yaml'];
if(dependency.version !== corpus.reference.yaml)throw new Error('固定YAML锁版本不匹配');
const response=await fetch(dependency.resolved);
if(!response.ok)throw new Error('固定YAML下载失败：'+response.status);
const archive=Buffer.from(await response.arrayBuffer());
const [algorithm,hash]=dependency.integrity.split('-');
if(createHash(algorithm).update(archive).digest('base64') !== hash)throw new Error('固定YAML包integrity不匹配');
const archiveUrl=new URL('yaml.tgz',cache),yamlDir=new URL('yaml/',cache);
await fs.writeFile(archiveUrl,archive);await fs.mkdir(yamlDir,{recursive:true});
await promisify(execFile)('tar',['-xzf',fileURLToPath(archiveUrl),'-C',fileURLToPath(yamlDir),'--strip-components=1']);
const require=createRequire(new URL('loader.cjs',cache));
const yaml=require('./yaml/dist/index.js');
const MarkdownIt=require('./markdown-it.cjs');
const strip=text=>stripTypeScriptTypes(text.replace(/^import .*;\r?\n/gm,'').replace(/^export /gm,''),{mode:'strip'});
const context={yaml,Error,Date,String,console,vscode:{workspace:{getConfiguration:()=>({get:(_key,fallback)=>fallback})},l10n:{t:text=>text}}};
vm.createContext(context);
vm.runInContext(strip(texts['dom.ts'])+'\n'+strip(texts['yamlPreamble.ts'])+'\nglobalThis.extend=extendMarkdownIt;',context);
const md=new MarkdownIt({html:true,breaks:false,linkify:true,typographer:false});
md.linkify.set({fuzzyLink:false});context.extend(md);
for(const item of corpus.cases){
 const tokens=md.parse(item.source,{});
 item.html=md.renderer.render(tokens,md.options,{});
 const fm=tokens.find(token=>token.type==='front_matter');
 item.frontMatter=fm?{type:fm.type,map:fm.map,meta:fm.meta}:null;
 item.yamlError=null;
 if(fm){try{yaml.parse(fm.meta.content);}catch(error){item.yamlError={code:error.code,pos:error.pos,linePos:error.linePos};}}
}
await fs.writeFile(output,JSON.stringify(corpus,null,2)+'\n');
console.log('固定frontmatter语义oracle完成：'+corpus.cases.length+'例');
