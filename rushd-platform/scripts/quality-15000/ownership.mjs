import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { readFile, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

export async function verifyOwnedServer(work) {
  const proof=JSON.parse(await readFile(join(work,'owner.json'),'utf8'))
  assert.match(proof.owner,/^rushd-quality-[0-9]+-[0-9]+-[0-9]+$/)
  assert.match(proof.container,/^[0-9a-f]{64}$/)
  assert.ok(Number.isSafeInteger(proof.pid)&&proof.pid>1)
  assert.ok(Number.isSafeInteger(proof.pgPort)&&proof.pgPort>0&&proof.pgPort<=65535)
  const identity=execFileSync('docker',['inspect',proof.container,'--format','{{.Id}}|{{.Name}}|{{index .Config.Labels "com.rushd.quality.owner"}}'],{encoding:'utf8'}).trim()
  assert.equal(identity,`${proof.container}|/${proof.owner}|${proof.owner}`,'Owned container identity mismatch')
  assert.equal(execFileSync('docker',['port',proof.container,'5432/tcp'],{encoding:'utf8'}).trim(),`127.0.0.1:${proof.pgPort}`,'Owned PG binding mismatch')
  const command=execFileSync('ps',['-p',String(proof.pid),'-o','command='],{encoding:'utf8'})
  for(const marker of [`--spring.application.name=${proof.owner}`,'--server.address=127.0.0.1','--server.port=18096',`--spring.datasource.url=jdbc:postgresql://127.0.0.1:${proof.pgPort}/rushd_quality`,`--rushd.images.root=${work}/images`])
    assert.ok(command.includes(marker),'Owned process configuration mismatch; command deliberately not printed')
  const listeners=execFileSync('lsof',['-nP','-t','-iTCP:18096','-sTCP:LISTEN'],{encoding:'utf8'}).trim().split(/\s+/)
  assert.deepEqual([...new Set(listeners)],[String(proof.pid)],'Owned HTTP listener mismatch')
}
if(process.argv[1]===fileURLToPath(import.meta.url)){
  const [work,owner,container,pid,pgPort]=process.argv.slice(2)
  assert.ok(work&&owner&&container&&pid&&pgPort)
  await writeFile(join(work,'owner.json'),JSON.stringify({owner,container,pid:Number(pid),pgPort:Number(pgPort)})+'\n',{flag:'wx',mode:0o600})
  await verifyOwnedServer(work)
}
