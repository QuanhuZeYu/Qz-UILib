'use strict';

const fs = require('node:fs');

async function main() {
  if (!process.env.GITHUB_OUTPUT || !process.env.GITHUB_STEP_SUMMARY)
    throw new Error('缺少 GitHub Actions 输出路径');
  const repository = process.env.EXPLICIT_MAVEN_REPOSITORY || '';
  if (/[\r\n]/.test(repository)) throw new Error('Maven 仓地址不能包含换行');
  if (repository) {
    fs.appendFileSync(process.env.GITHUB_OUTPUT, 'ready=true\n');
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, '已指定 Maven 仓，跳过公开 Release 检查并继续完整构建。\n');
    return;
  }

  const releases = JSON.parse(process.env.UPSTREAM_RELEASES || 'null');
  if (!Array.isArray(releases) || !releases.length) throw new Error('必须声明上游 repo/tag/asset 列表');
  for (const release of releases) {
    if (!release || typeof release.repo !== 'string' || !/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(release.repo)
        || typeof release.tag !== 'string' || !/^v?\d+\.\d+\.\d+(?:[.-](?:alpha|beta|rc)(?:[.-]?\d+)?)?$/.test(release.tag)
        || typeof release.asset !== 'string' || !/^[A-Za-z0-9][A-Za-z0-9._-]*\.zip$/.test(release.asset))
      throw new Error('上游 repo/tag/asset 配置无效');
  }
  const headers = { Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28' };
  if (process.env.GH_TOKEN) headers.Authorization = 'Bearer ' + process.env.GH_TOKEN;
  const missing = [];
  for (const { repo, tag, asset } of releases) {
    const base = 'https://api.github.com/repos/' + repo;
    const response = await fetch(base + '/releases/tags/' + encodeURIComponent(tag),
      { headers, signal: AbortSignal.timeout(30000) });
    if (response.status === 404) {
      // 只有存在的仓库缺少公开 Release 才等待；错仓库或访问失败仍是配置/认证错误。
      const check = await fetch(base, { headers, signal: AbortSignal.timeout(30000) });
      if (!check.ok) throw new Error(repo + ' 仓库检查失败 HTTP ' + check.status);
      missing.push(repo + ' ' + tag + '：Release 尚未公开（HTTP 404）');
      continue;
    }
    if (!response.ok) throw new Error(repo + ' Release 检查失败 HTTP ' + response.status);
    const release = await response.json();
    if (release.tag_name !== tag || typeof release.draft !== 'boolean' || !Array.isArray(release.assets))
      throw new Error(repo + ' Release 响应与请求不符');
    if (release.draft) missing.push(repo + ' ' + tag + '：Release 仍为草稿');
    else if (!release.assets.some(candidate => candidate.name === asset && candidate.state === 'uploaded'))
      missing.push(repo + ' ' + tag + '：缺少已上传资产 ' + asset);
  }
  const ready = missing.length === 0;
  fs.appendFileSync(process.env.GITHUB_OUTPUT, 'ready=' + ready + '\n');
  const summary = ready ? '上游 Release 及精确 Maven ZIP 已就绪，继续完整构建。\n'
    : '## 等待上游发布\n\n' + missing.map(reason => '- ' + reason).join('\n')
      + '\n\n本次未执行依赖下载及完整构建；上游公开后可重新运行普通 CI。\n';
  fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, summary);
  console.log(summary);
}

// 不把认证、网络、服务端或配置错误转换为 ready=false，避免跳过真实故障。
main().catch(error => { console.error('上游 Release 检查失败：' + error.message); process.exitCode = 1; });
