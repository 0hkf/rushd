import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createAdminImagesManager, imageDraftDirty, imageWorkPending } from './adminImages.js'
import { imagePath } from './propertyImages.js'

const uuid = (n) => `11111111-1111-4111-8111-${String(n).padStart(12,'0')}`
const image = (n, order=0) => ({id:uuid(n),order,altText:null,thumbnail:{url:imagePath(15,uuid(n),'thumbnail'),width:640,height:480},display:{url:imagePath(15,uuid(n),'display'),width:960,height:720}})
const deferred = () => {let resolve;const promise=new Promise((r)=>resolve=r);return {promise,resolve}}
function setup(count=0) {
  let server={revision:'9007199254740993',primaryImageId:count?uuid(1):null,images:Array.from({length:count},(_,i)=>image(i+1,i))}, sequence=100, calls=[],releases=[],tokens=new Map(),deleted=new Set()
  const bump=()=>server.revision=(BigInt(server.revision)+1n).toString()
  const client={
    async get(path){calls.push(['GET',path]);return {data:structuredClone(server)}},
    async post(path,form,config){calls.push(['POST',path,form,config]);const token=form.get('uploadToken')
      if(!tokens.has(token)){const item=image(++sequence,server.images.length);item.altText=form.get('altText');server.images.push(item);server.primaryImageId??=item.id;bump();tokens.set(token,item.id)}
      config.onUploadProgress?.({loaded:10,total:10});return {status:tokens.has(token)?200:201,data:structuredClone(server)}},
    async put(path,draft){calls.push(['PUT',path,structuredClone(draft)]);if(draft.expectedRevision!==server.revision)throw {response:{status:409}}
      server.images=draft.images.map((entry,i)=>({...server.images.find((item)=>item.id===entry.id),...entry,order:i}));server.primaryImageId=draft.primaryImageId;bump();return {data:structuredClone(server)}},
    async delete(path,config){calls.push(['DELETE',path,structuredClone(config.params)]);const id=path.split('/').at(-1)
      if(deleted.has(id))return {status:204}
      if(config.params.expectedRevision!==server.revision)throw {response:{status:409}}
      deleted.add(id);server.images=server.images.filter((entry)=>entry.id!==id).map((entry,i)=>({...entry,order:i}));
      if(server.primaryImageId===id)server.primaryImageId=config.params.replacementPrimaryImageId??null;bump();return {status:204}},
  }
  const manager=createAdminImagesManager(client,15,{uuid:()=>uuid(++sequence),preview:async(file)=>`blob:${file.name}`,revoke:(url)=>releases.push(url)})
  return {manager,client,calls,releases,get server(){return server},set server(value){server=value}}
}
async function queued(fixture,name='source.jpg') {
  await fixture.manager.enqueue([new File(['synthetic source'],name,{type:'image/jpeg'})])
  const entry=fixture.manager.getSnapshot().queue.at(-1)
  fixture.manager.changePending(entry.id,'rightsBasis','OWNED');fixture.manager.changePending(entry.id,'rightsConfirmed',true)
  return fixture.manager.getSnapshot().queue.at(-1)
}
test('new editor loads empty legacy manifest without changing a separate property form',async()=>{
  const f=setup(),propertyForm={price:'9007199254740993.01',area:'500.25',neighborhood:'النوارية'}
  await f.manager.load();assert.equal(f.manager.getSnapshot().phase,'ready');assert.equal(imageWorkPending(f.manager.getSnapshot()),false)
  assert.deepEqual(propertyForm,{price:'9007199254740993.01',area:'500.25',neighborhood:'النوارية'});assert.equal(f.calls.length,1)
})
test('rights failure keeps source and preview and never sends a POST',async()=>{
  const f=setup();await f.manager.load();await f.manager.enqueue([new File(['source'],'a.jpg',{type:'image/jpeg'})]);const original=f.manager.getSnapshot().queue[0]
  assert.equal(await f.manager.upload(original.id),false);const entry=f.manager.getSnapshot().queue[0]
  assert.equal(entry.file,original.file);assert.equal(entry.preview,original.preview);assert.ok(entry.error);assert.equal(f.calls.filter(([method])=>method==='POST').length,0)
})
test('accepted upload removes only its source/preview after authoritative manifest response',async()=>{
  const f=setup();await f.manager.load();const first=await queued(f,'a.jpg'),second=await queued(f,'b.jpg')
  assert.equal(await f.manager.upload(first.id),true);const state=f.manager.getSnapshot()
  assert.equal(state.manifest.images.length,1);assert.equal(state.manifest.primaryImageId,state.manifest.images[0].id)
  assert.equal(state.queue[0].id,second.id);assert.deepEqual(f.releases,['blob:a.jpg']);assert.equal(state.busy,'')
})
test('response lost after commit freezes the exact payload and safely retries with the same token',async()=>{
  const f=setup();await f.manager.load();const entry=await queued(f);const post=f.client.post;let first=true
  f.client.post=async(...args)=>{const response=await post(...args);if(first){first=false;throw new Error('response lost after commit')}return response}
  assert.equal(await f.manager.upload(entry.id),false);const uncertain=f.manager.getSnapshot().queue[0]
  assert.equal(uncertain.frozen,true);assert.equal(uncertain.status,'uncertain');assert.equal(f.server.images.length,1)
  f.manager.changePending(entry.id,'altText','must not change');assert.equal(f.manager.getSnapshot().queue[0].altText,null)
  assert.equal(await f.manager.upload(entry.id),true);const posts=f.calls.filter(([method])=>method==='POST')
  assert.equal(posts.length,2);assert.equal(posts[0][2].get('uploadToken'),posts[1][2].get('uploadToken'));assert.equal(f.server.images.length,1)
  assert.equal(f.manager.getSnapshot().queue.length,0);assert.deepEqual(f.releases,['blob:source.jpg'])
})
test('cancel stops waiting, ignores late success and retains immutable retry data',async()=>{
  const f=setup();await f.manager.load();const entry=await queued(f),wait=deferred(),post=f.client.post
  f.client.post=async(...args)=>{await wait.promise;return post(...args)}
  const pending=f.manager.upload(entry.id);assert.equal(f.manager.getSnapshot().busy,'upload');f.manager.cancelUpload()
  assert.equal(f.manager.getSnapshot().queue[0].status,'uncertain');assert.equal(f.manager.getSnapshot().queue[0].token,entry.token)
  wait.resolve();await pending;assert.equal(f.manager.getSnapshot().queue.length,1);assert.equal(f.manager.getSnapshot().manifest.images.length,0)
  assert.equal(await f.manager.upload(entry.id),true);assert.equal(f.server.images.length,1)
})
for(const status of [400,413,415,429,503])test(`known rejection ${status} preserves file and allows explicit correction`,async()=>{
  const f=setup();await f.manager.load();const entry=await queued(f);f.client.post=async()=>{throw {response:{status}}}
  assert.equal(await f.manager.upload(entry.id),false);assert.equal(f.manager.getSnapshot().queue[0].file,entry.file)
  assert.equal(f.manager.getSnapshot().queue[0].frozen,false);f.manager.changePending(entry.id,'altText','تصحيح صريح')
  assert.notEqual(f.manager.getSnapshot().queue[0].token,entry.token);assert.equal(f.manager.getSnapshot().queue[0].status,'pending')
})
test('batch uploads are sequential and stop on first failed file without losing later files',async()=>{
  const f=setup();await f.manager.load();await queued(f,'a.jpg');await queued(f,'b.jpg');let active=0,max=0;const post=f.client.post
  f.client.post=async(...args)=>{active++;max=Math.max(max,active);await new Promise((resolve)=>setTimeout(resolve,1));try{return await post(...args)}finally{active--}}
  await f.manager.uploadAll();assert.equal(max,1);assert.equal(f.server.images.length,2);assert.equal(f.manager.getSnapshot().queue.length,0)
  const g=setup();await g.manager.load();const first=await queued(g,'c.jpg'),second=await queued(g,'d.jpg');let sent=0
  g.client.post=async()=>{sent++;throw {response:{status:503}}};await g.manager.uploadAll()
  assert.equal(sent,1);assert.equal(g.manager.getSnapshot().queue.length,2);assert.equal(g.manager.getSnapshot().queue.find((entry)=>entry.id===second.id).status,'pending')
  assert.equal(g.manager.getSnapshot().queue.find((entry)=>entry.id===first.id).status,'failed')
})
test('reorder keeps primary unless selected, saves full set and exact revision, never deletes by omission',async()=>{
  const f=setup(2);await f.manager.load();f.manager.move(uuid(1),1)
  assert.equal(f.manager.getSnapshot().draft.primaryImageId,uuid(1));f.manager.primary(uuid(2));f.manager.editSaved(uuid(1),'واجهة العقار')
  assert.equal(imageDraftDirty(f.manager.getSnapshot()),true);assert.equal(await f.manager.save(),true)
  const payload=f.calls.find(([method])=>method==='PUT')[2]
  assert.equal(payload.expectedRevision,'9007199254740993');assert.deepEqual(payload.images.map((entry)=>entry.id),[uuid(2),uuid(1)])
  assert.equal(f.server.primaryImageId,uuid(2));assert.equal(imageDraftDirty(f.manager.getSnapshot()),false)
})
test('stale save preserves draft and chosen files until explicit reload confirmation',async()=>{
  const f=setup(1);await f.manager.load();const entry=await queued(f);f.manager.editSaved(uuid(1),'مسودتي')
  f.server.revision='9007199254740994';assert.equal(await f.manager.save(),false)
  assert.equal(f.manager.getSnapshot().conflict,true);assert.equal(f.manager.getSnapshot().draft.images[0].altText,'مسودتي')
  assert.equal(await f.manager.load(),false);assert.equal(f.manager.getSnapshot().draft.images[0].altText,'مسودتي')
  assert.equal(await f.manager.load(true),true);assert.equal(f.manager.getSnapshot().draft.images[0].altText,null)
  assert.equal(f.manager.getSnapshot().queue[0].file,entry.file)
})
test('dirty draft blocks uploads and deletes; discard is local and does not make a request',async()=>{
  const f=setup(1);await f.manager.load();const entry=await queued(f);f.manager.editSaved(uuid(1),'changed')
  assert.equal(await f.manager.upload(entry.id),false);assert.equal(await f.manager.remove(uuid(1)),false)
  assert.equal(f.calls.length,1);f.manager.discardDraft();assert.equal(imageDraftDirty(f.manager.getSnapshot()),false);assert.equal(f.calls.length,1)
})
test('primary deletion requires explicit replacement and last image allows none',async()=>{
  const f=setup(2);await f.manager.load();assert.equal(await f.manager.remove(uuid(1)),false);assert.equal(f.calls.filter(([method])=>method==='DELETE').length,0)
  assert.equal(await f.manager.remove(uuid(1),uuid(2)),true);assert.equal(f.manager.getSnapshot().manifest.primaryImageId,uuid(2))
  assert.equal(await f.manager.remove(uuid(2)),true);assert.equal(f.manager.getSnapshot().manifest.primaryImageId,null)
})
test('202 cleanup remains explicit and retries the same id/revision until 204',async()=>{
  const f=setup(1);await f.manager.load();const remove=f.client.delete;let first=true
  f.client.delete=async(...args)=>{const result=await remove(...args);if(first){first=false;return {status:202}}return result}
  assert.equal(await f.manager.remove(uuid(1)),true);let state=f.manager.getSnapshot()
  assert.equal(state.manifest.images.length,0);assert.equal(state.pendingDeletes.length,1);assert.equal(state.pendingDeletes[0].confirmed,true)
  assert.equal(imageWorkPending(state),true);assert.equal(await f.manager.remove(uuid(1),null,true),true)
  assert.equal(f.manager.getSnapshot().pendingDeletes.length,0)
  const deletes=f.calls.filter(([method])=>method==='DELETE');assert.deepEqual(deletes[0][2],deletes[1][2])
})
test('accepted 202 then failed manifest read must never be misreported as rejected deletion',async()=>{
  const f=setup(1);await f.manager.load();const remove=f.client.delete
  f.client.delete=async(...args)=>{await remove(...args);return {status:202}};f.client.get=async()=>{throw {response:{status:404}}}
  await f.manager.remove(uuid(1));const state=f.manager.getSnapshot()
  assert.equal(state.phase,'error');assert.equal(state.pendingDeletes.length,1);assert.equal(state.pendingDeletes[0].confirmed,true)
})
test('12 saved/selected limit rejects overflow before previewing and retains previous selections',async()=>{
  const f=setup(11);await f.manager.load();await queued(f,'one.jpg');await f.manager.enqueue([new File(['source'],'extra.jpg',{type:'image/jpeg'})])
  assert.equal(f.manager.getSnapshot().queue.length,1);assert.match(f.manager.getSnapshot().error,/12/)
})
test('a hidden pending-cleanup image still occupies a slot and prevents new uploads',async()=>{
  const f=setup(12);await f.manager.load();const remove=f.client.delete
  f.client.delete=async(...args)=>{await remove(...args);return {status:202}}
  await f.manager.remove(uuid(12));assert.equal(f.manager.getSnapshot().manifest.images.length,11)
  await f.manager.enqueue([new File(['source'],'extra.jpg',{type:'image/jpeg'})])
  assert.equal(f.manager.getSnapshot().queue.length,0);assert.match(f.manager.getSnapshot().error,/12/)
  assert.equal(await f.manager.uploadAll(),undefined);assert.equal(f.calls.filter(([method])=>method==='POST').length,0)
})
test('invalid preview does not post and unmount revokes late preview exactly once',async()=>{
  const f=setup();const wait=deferred(),revoked=[];const manager=createAdminImagesManager(f.client,15,{uuid:()=>uuid(900),preview:()=>wait.promise,revoke:(url)=>revoked.push(url)})
  await manager.load();const pending=manager.enqueue([new File(['source'],'a.jpg',{type:'image/jpeg'})]);manager.dispose();wait.resolve('blob:late');await pending
  assert.deepEqual(revoked,['blob:late']);assert.equal(manager.getSnapshot().queue.length,0)
  const bad=createAdminImagesManager(f.client,15,{preview:async()=>{throw new Error('bad image')}});await bad.load();await bad.enqueue([new File(['x'],'bad.jpg',{type:'image/jpeg'})])
  assert.equal(bad.getSnapshot().queue[0].status,'invalid');assert.equal(await bad.upload(bad.getSnapshot().queue[0].id),false)
})
test('dispose/remove release selected object URLs without deleting already saved server images',async()=>{
  const f=setup(1);await f.manager.load();const first=await queued(f,'a.jpg');await queued(f,'b.jpg')
  f.manager.removePending(first.id);f.manager.dispose();assert.deepEqual(f.releases,['blob:a.jpg','blob:b.jpg']);assert.equal(f.server.images.length,1)
})
