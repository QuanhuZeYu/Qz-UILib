'use strict';

const fs = require('node:fs');
const mode = process.argv[2];
if (!['ci', 'release'].includes(mode)) throw new Error('必须明确 ci 或 release 模式');
const properties = fs.readFileSync('gradle.properties', 'utf8');
function property(name) {
  const values = [...properties.matchAll(new RegExp('^' + name + '[ \t]*=[ \t]*(.*)$', 'gm'))];
  if (values.length > 1) throw new Error('gradle.properties 重复声明 ' + name);
  return values[0]?.[1].trim() || '';
}
const versionPattern = /^\d+\.\d+\.\d+(?:[.-](alpha|beta|rc)(?:[.-]?\d+)?)?$/;
const declared = property('uilibVersion');
if (!versionPattern.test(declared)) throw new Error('uilibVersion 声明必须是精确稳定版或 alpha/beta/rc 版本');
const version = mode === 'release' ? process.env.RELEASE_TAG : process.env.OVERRIDE_VERSION || declared;
const match = versionPattern.exec(version || '');
if (!match) throw new Error('版本必须是精确稳定版或 alpha/beta/rc 版本');
if (mode === 'release') {
  if (version !== declared) throw new Error('tag 与 gradle.properties 的 uilibVersion 声明不同');
  if (process.env.CONFIRM !== 'PUBLISH ' + version) throw new Error('确认短语必须为 PUBLISH ' + version);
}
const group = process.env.OVERRIDE_GROUP || property('uilibGroup')
  || (version.startsWith('4.') ? 'club.heiqi.uilib' : 'asia.quanhuzeyu.uilib');
if (!/^[A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*$/.test(group)) throw new Error('Maven group 格式无效');
const output = { version, group };
if (mode === 'release') {
  output.prerelease = String(Boolean(match[1]));
} else {
  const repository = process.env.OVERRIDE_REPOSITORY || '';
  if (/[\r\n]/.test(repository)) throw new Error('Maven 仓地址不能包含换行');
  output.repository = repository;
  output.download = String(!repository);
}
fs.appendFileSync(process.env.GITHUB_OUTPUT, Object.entries(output).map(([key, value]) => key + '=' + value + '\n').join(''));
