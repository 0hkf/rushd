import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createFavoritesStore, createFavoritesPageLoader, favoriteError, favoriteId, readFavoriteStatus, readFavoritesPage } from './favorites.js'

const deferred = () => { let resolve, reject; const promise = new Promise((a,b) => { resolve=a;reject=b });return { promise,resolve,reject } }
const summary = { id:'15',title:'عقار صناعي',type:'LAND',listingType:'SALE',rentalPeriod:null,city:'مكة',district:'النوارية',price:'9007199254740993.01',area:'500.25',status:'ACTIVE',primaryImage:null }
const row = { propertyId:'15',createdAt:'2026-10-21T10:00:00Z',available:true,property:summary }
const page = (rows=[row],number=0) => ({ content:rows,page:number,size:12,totalElements:rows.length,totalPages:rows.length?1:0,first:number===0,last:true })
test('404 from status/list is a service read failure, not a claim that a published property is unavailable', async () => {
  const error = { response: { status: 404 } }
  assert.match(favoriteError(error, 'status'), /قراءة حالة المفضلة/)
  assert.match(favoriteError(error, 'list'), /خدمة المفضلة/)
  assert.match(favoriteError(error), /العقار غير متاح للحفظ/)
  const f = setup([])
  f.client.get = async () => { throw error }
  await f.store.ensure(['15'])
  assert.match(f.store.getSnapshot().entries['15'].error, /لا يعني ذلك أن العقار غير متاح/)
  assert.equal(f.store.getSnapshot().entries['15'].saved, undefined)
  let result
  await createFavoritesPageLoader(f.client, (state) => { result = state }).load(0)
  assert.equal(result.phase, 'error')
  assert.equal(result.result, null)
  assert.match(result.error, /خدمة المفضلة/)
})
function setup(saved=['15']) {
  const server=new Set(saved),calls=[]
  const client={
    async get(path,config) { calls.push(['GET',path,config]);return { data:{favoritePropertyIds:config.params.propertyIds.split(',').filter((id)=>server.has(id))} } },
    async put(path,body,config) { calls.push(['PUT',path,body,config]);server.add(path.split('/').at(-1));return {status:204} },
    async delete(path,config) { calls.push(['DELETE',path,config]);server.delete(path.split('/').at(-1));return {status:204} },
  }
  return {server,calls,client,store:createFavoritesStore(client)}
}
test('favorite ids preserve exact Long strings without accepting unsafe Number or traversal',()=>{
  assert.equal(favoriteId('9007199254740993'),'9007199254740993');assert.equal(favoriteId(15),'15')
  for(const id of [Number.MAX_SAFE_INTEGER+2,'0','01','-1','1.0','1e3','9223372036854775808','../15',null])assert.throws(()=>favoriteId(id))
})
test('status response cannot inject ids outside the requested batch or duplicates',()=>{
  assert.deepEqual([...readFavoriteStatus({favoritePropertyIds:['15']},['15','16'])],['15'])
  for(const raw of [{},{favoritePropertyIds:['17']},{favoritePropertyIds:['15','15']},{favoritePropertyIds:[15]}])assert.throws(()=>readFavoriteStatus(raw,['15']))
})
test('precise summary and hidden placeholder remain distinct',()=>{
  assert.equal(readFavoritesPage(page()).content[0].property.price,'9007199254740993.01')
  const hidden={...row,available:false,property:null};assert.equal(readFavoritesPage(page([hidden])).content[0].property,null)
  assert.throws(()=>readFavoritesPage(page([{...hidden,property:summary}])))
})
test('invalid private summaries, numeric prices and malformed pagination are rejected',()=>{
  for(const change of [{status:'DRAFT'},{price:Number.MAX_SAFE_INTEGER+2},{area:'1e3'},{id:'16'},{id:15},{listingType:'RENT',rentalPeriod:null}])
    assert.throws(()=>readFavoritesPage(page([{...row,property:{...summary,...change}}])))
  for(const change of [{size:101},{page:-1},{totalPages:3},{totalElements:-1}])assert.throws(()=>readFavoritesPage({...page(),...change}))
})
test('one status batch serves all cards and repeated ensures do not fetch per button',async()=>{
  const f=setup();await f.store.ensure(['15','16','15']);await f.store.ensure(['15','16'])
  assert.equal(f.calls.length,1);assert.equal(f.calls[0][2].params.propertyIds,'15,16')
  assert.equal(f.store.getSnapshot().entries['15'].saved,true);assert.equal(f.store.getSnapshot().entries['16'].saved,false)
})
test('batch limits reject before issuing a request',async()=>{
  const f=setup();await assert.rejects(f.store.ensure(Array.from({length:101},(_,i)=>String(i+1))))
  assert.equal(f.calls.length,0)
})
test('membership changes only after authoritative 204 and rapid clicks have one request',async()=>{
  const f=setup([]);await f.store.ensure(['15']);const wait=deferred();f.client.put=async()=>wait.promise
  const save=f.store.toggle('15');assert.equal(f.store.getSnapshot().entries['15'].saved,false)
  assert.equal(await f.store.toggle('15'),false);assert.equal(f.store.getSnapshot().version,0)
  wait.resolve({status:204});assert.equal(await save,true);assert.equal(f.store.getSnapshot().entries['15'].saved,true);assert.equal(f.store.getSnapshot().version,1)
})
test('removal uses DELETE and only updates the current account store',async()=>{
  const f=setup();await f.store.ensure(['15']);assert.equal(await f.store.toggle('15'),true)
  assert.equal(f.calls.at(-1)[0],'DELETE');assert.equal(f.store.getSnapshot().entries['15'].saved,false)
  const other=createFavoritesStore(f.client);assert.deepEqual(other.getSnapshot().entries,{})
})
test('lost PUT response retries PUT with the same target instead of toggling to delete',async()=>{
  const f=setup([]);await f.store.ensure(['15']);const put=f.client.put;let first=true
  f.client.put=async(...args)=>{const result=await put(...args);if(first){first=false;throw new Error('response lost')}return result}
  assert.equal(await f.store.toggle('15'),false);assert.equal(f.server.has('15'),true);assert.equal(f.store.getSnapshot().entries['15'].phase,'uncertain')
  assert.equal(await f.store.toggle('15'),true);assert.deepEqual(f.calls.filter(([method])=>method!=='GET').map(([method])=>method),['PUT','PUT'])
})
test('lost DELETE response retries DELETE and does not re-add the property',async()=>{
  const f=setup();await f.store.ensure(['15']);const remove=f.client.delete;let first=true
  f.client.delete=async(...args)=>{const result=await remove(...args);if(first){first=false;throw new Error('lost')}return result}
  await f.store.toggle('15');await f.store.toggle('15');assert.equal(f.server.has('15'),false)
  assert.deepEqual(f.calls.filter(([method])=>method!=='GET').map(([method])=>method),['DELETE','DELETE'])
})
for(const status of [400,401,403,404,503])test(`HTTP ${status} keeps prior membership and exposes error without optimistic success`,async()=>{
  const f=setup([]);await f.store.ensure(['15']);f.client.put=async()=>{throw {response:{status}}}
  await f.store.toggle('15');const entry=f.store.getSnapshot().entries['15'];assert.equal(entry.saved,false);assert.equal(entry.phase,'error');assert.ok(entry.error)
})
test('failed initial status can be explicitly retried without sending mutation',async()=>{
  const f=setup(),get=f.client.get;f.client.get=async()=>{throw {response:{status:503}}};await f.store.ensure(['15']);assert.equal(f.store.getSnapshot().entries['15'].saved,undefined)
  f.client.get=get;await f.store.toggle('15');assert.equal(f.store.getSnapshot().entries['15'].saved,true);assert.equal(f.calls.filter(([m])=>m!=='GET').length,0)
})
test('explicit reconciliation resolves uncertain outcome from server',async()=>{
  const f=setup([]);await f.store.ensure(['15']);f.client.put=async()=>{f.server.add('15');throw new Error('lost')}
  await f.store.toggle('15');await f.store.ensure(['15'],true);assert.equal(f.store.getSnapshot().entries['15'].saved,true);assert.equal(f.store.getSnapshot().entries['15'].phase,'ready')
})
test('dispose clears membership and rejects late status even if client ignores abort',async()=>{
  const f=setup(),wait=deferred();f.client.get=async()=>wait.promise
  const pending=f.store.ensure(['15']);f.store.dispose();wait.resolve({data:{favoritePropertyIds:['15']}});await pending
  assert.deepEqual(f.store.getSnapshot(),{entries:{},version:0})
})
test('dispose aborts mutation and a late success cannot restore prior account data',async()=>{
  const f=setup([]);await f.store.ensure(['15']);const wait=deferred();let signal;f.client.put=async(path,body,config)=>{signal=config.signal;return wait.promise}
  const pending=f.store.toggle('15');f.store.dispose();assert.equal(signal.aborted,true);wait.resolve({status:204});await pending;assert.deepEqual(f.store.getSnapshot().entries,{})
})
test('page loader cancels old page and does not publish late private results',async()=>{
  const a=deferred(),b=deferred(),states=[];let calls=0
  const loader=createFavoritesPageLoader({get:()=>++calls===1?a.promise:b.promise},(state)=>states.push(state))
  const first=loader.load(0),second=loader.load(1);b.resolve({data:{...page([],1),totalElements:13,totalPages:2}});await second
  a.resolve({data:page()});await first;assert.equal(states.at(-1).result.page,1)
  const wait=deferred(),after=[];const canceled=createFavoritesPageLoader({get:()=>wait.promise},s=>after.push(s));const task=canceled.load(0);canceled.cancel();wait.resolve({data:page()});await task
  assert.equal(after.at(-1).phase,'loading')
})
test('page errors never substitute a previous list or claim empty favorites',async()=>{
  const states=[],loader=createFavoritesPageLoader({get:async()=>{throw {response:{status:503}}}},s=>states.push(s))
  await loader.load(0);assert.equal(states.at(-1).phase,'error');assert.equal(states.at(-1).result,null)
})
