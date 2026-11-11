import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { join } from 'node:path'
const dir=process.argv[2],counts={frontend:9000,backend:4000,http:2000},all=[],files={}
for(const [layer,count]of Object.entries(counts)){
  const bytes=await readFile(join(dir,`${layer}.jsonl`)),rows=bytes.toString().trim().split('\n').map(JSON.parse)
  assert.equal(rows.length,count,`Incomplete ${layer} matrix`);assert.ok(rows.every(r=>r.layer===layer&&['passed','failed'].includes(r.status)))
  assert.equal(new Set(rows.map(r=>r.id)).size,count);files[layer]={sha256:createHash('sha256').update(bytes).digest('hex'),total:count,passed:rows.filter(r=>r.status==='passed').length,failed:rows.filter(r=>r.status==='failed').length};all.push(...rows)
}
assert.equal(all.length,15000);assert.equal(new Set(all.map(r=>r.id)).size,15000)
const groups={}
for(const row of all){const key=`${row.layer}.${row.group}`;groups[key]??={total:0,passed:0,failed:0};groups[key].total++;groups[key][row.status]++}
const summary={schemaVersion:1,seed:'deterministic-contract-matrix-v1',completedAt:new Date().toISOString(),total:15000,passed:all.filter(r=>r.status==='passed').length,failed:all.filter(r=>r.status==='failed').length,skipped:0,files,groups,failures:all.filter(r=>r.status==='failed').map(({id,input,expected,actual,error})=>({id,input,expected,actual,error}))}
await writeFile(join(dir,'summary.json'),JSON.stringify(summary,null,2)+'\n',{flag:'wx',mode:0o600})
await writeFile(join(dir,'cases.csv'),'id,layer,group,status\n'+all.map(r=>[r.id,r.layer,r.group,r.status].map(v=>'"'+v.replaceAll('"','""')+'"').join(',')).join('\n')+'\n',{flag:'wx',mode:0o600})
console.log(JSON.stringify({total:summary.total,passed:summary.passed,failed:summary.failed,skipped:0,files},null,2))
process.exitCode=summary.failed?2:0
