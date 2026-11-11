import { test } from 'node:test'
import assert from 'node:assert/strict'
import { canViewPropertyImages, createImageLoader, createImagesApi, imageAlt, imageError, imagePath, imagePropertyId,
  inspectImage, readImageDescriptor, readImageManifest, uploadFieldsError, validateImageFile } from './propertyImages.js'

const id = '11111111-1111-4111-8111-111111111111', other = '22222222-2222-4222-8222-222222222222'
const descriptor = (imageId = id, order = 0) => ({ id: imageId, order, altText: null,
  thumbnail: { url: imagePath(15, imageId, 'thumbnail'), width: 640, height: 480 },
  display: { url: imagePath(15, imageId, 'display'), width: 960, height: 720 } })
const manifest = () => ({ revision: '9007199254740993', primaryImageId: id, images: [descriptor()] })
const deferred = () => { let resolve, reject; const promise = new Promise((a,b) => {resolve=a;reject=b}); return {promise,resolve,reject} }
function png(width = 640, height = 480, depth = 8, color = 2, animation = false) {
  const bytes = new Uint8Array(animation ? 65 : 45), view = new DataView(bytes.buffer)
  bytes.set([137,80,78,71,13,10,26,10]);view.setUint32(8,13);bytes.set([73,72,68,82],12)
  view.setUint32(16,width);view.setUint32(20,height);bytes[24]=depth;bytes[25]=color
  let offset=33
  if(animation){view.setUint32(offset,8);bytes.set([97,99,84,76],offset+4);offset+=20}
  bytes.set([73,69,78,68],offset+4)
  return new File([bytes],'header.png',{type:'image/png'}) // Header preflight fixture, not a decoded PNG.
}
function jpeg(width=640,height=480,depth=8,components=3) {
  const bytes=new Uint8Array(23),view=new DataView(bytes.buffer)
  bytes.set([255,216,255,192,0,17,depth]);view.setUint16(7,height);view.setUint16(9,width);bytes[11]=components
  return new File([bytes],'header.jpg',{type:'image/jpeg'})
}
test('image identity/path keeps exact long strings and fixed local variants',()=>{
  assert.equal(imagePropertyId('9223372036854775807'),'9223372036854775807')
  assert.equal(imagePath(15,id,'display'),`/api/properties/15/images/${id}/display`)
  for(const value of [0,-1,1.5,9007199254740992,'01','1/evil','1?x=1','9223372036854775808',null,{}])assert.throws(()=>imagePropertyId(value))
  for(const variant of ['original','../display','https://evil.example'])assert.throws(()=>imagePath(15,id,variant))
})
test('manifest revision stays a precise string and empty legacy manifests work',()=>{
  assert.equal(readImageManifest(manifest(),15).revision,'9007199254740993')
  assert.deepEqual(readImageManifest({revision:'0',primaryImageId:null,images:[]},15),{revision:'0',primaryImageId:null,images:[]})
  for(const revision of [1,null,'01','-1','1e3','9223372036854775808'])assert.throws(()=>readImageManifest({...manifest(),revision},15))
})
test('external, foreign-property, query and traversal image URLs are rejected',()=>{
  for(const url of ['https://evil.example/photo.jpg','//evil.example/x','javascript:alert(1)',`/api/properties/16/images/${id}/thumbnail`,imagePath(15,id,'thumbnail')+'?token=x','/uploads/raw.jpg']){
    const raw=descriptor();raw.thumbnail.url=url;assert.throws(()=>readImageDescriptor(raw,15))
  }
})
test('invalid sizes/order/alt, duplicate images, missing primary and excess sets are rejected',()=>{
  for(const patch of [{order:-1},{order:12},{altText:'a'.repeat(161)},{altText:'<script>'},{id:'not-uuid'}])assert.throws(()=>readImageDescriptor({...descriptor(),...patch},15))
  for(const size of [0,-1,641,1.5]){const raw=descriptor();raw.thumbnail.width=size;assert.throws(()=>readImageDescriptor(raw,15))}
  for(const patch of [{primaryImageId:other},{images:[descriptor(id,0),descriptor(id,1)]},{images:[descriptor(id,1)]},{images:Array.from({length:13},()=>descriptor())}])assert.throws(()=>readImageManifest({...manifest(),...patch},15))
})
test('visibility is public ACTIVE/SOLD or private ADMIN, never SELLER/BUYER',()=>{
  for(const status of ['ACTIVE','SOLD'])assert.equal(canViewPropertyImages({status},null),true)
  for(const status of ['DRAFT','INACTIVE']){
    assert.equal(canViewPropertyImages({status},{role:'ADMIN'}),true)
    for(const role of ['BUYER','SELLER',undefined])assert.equal(canViewPropertyImages({status},{role}),false)
  }
  assert.equal(canViewPropertyImages({},null),false);assert.equal(canViewPropertyImages({},null,true),true)
})
test('alt fallback uses title and position, not filename',()=>{
  assert.equal(imageAlt({altText:'واجهة'},'عنوان',0),'واجهة')
  assert.equal(imageAlt({altText:' '},'عنوان',1),'صورة 2 — عنوان')
})
test('size, MIME, extension, empty and unsafe names are validated including exact 8MiB',()=>{
  const valid={size:8*1024*1024,name:'صورة.jpg',type:'image/jpeg'};assert.equal(validateImageFile(valid),null)
  for(const patch of [{size:0},{size:valid.size+1},{name:'../x.jpg'},{name:'a\\x.jpg'},{name:'a\0.jpg'},{name:'a'.repeat(201)+'.jpg'},{name:'x.png'},{type:'image/svg+xml'},{type:'image/webp'},{type:''}])assert.ok(validateImageFile({...valid,...patch}))
})
for(const [width,height] of [[640,480],[480,640],[4000,4000],[8000,2000]])test(`header boundary accepted ${width}x${height}`,async()=>{
  assert.deepEqual(await inspectImage(png(width,height)),{width,height});assert.deepEqual(await inspectImage(jpeg(width,height)),{width,height})
})
for(const [width,height] of [[640,479],[639,480],[8001,480],[4001,4000],[0,640]])test(`header boundary rejected ${width}x${height}`,async()=>{
  await assert.rejects(inspectImage(png(width,height)));await assert.rejects(inspectImage(jpeg(width,height)))
})
test('APNG, 16-bit, indexed PNG, CMYK and fake headers are rejected before preview',async()=>{
  for(const file of [png(640,480,8,2,true),png(640,480,16),png(640,480,8,3),jpeg(640,480,8,4),jpeg(640,480,12),new File(['https://maps.example'],'fake.jpg',{type:'image/jpeg'})])await assert.rejects(inspectImage(file))
})
test('attestation and plain optional texts are checked, with a required LICENSED reference',()=>{
  const entry={altText:null,rightsBasis:'OWNED',rightsConfirmed:true,sourceReference:null};assert.equal(uploadFieldsError(entry),null)
  for(const patch of [{rightsConfirmed:false},{rightsBasis:''},{rightsBasis:'LICENSED'},{altText:'<b>x</b>'},{altText:'a'.repeat(161)},{sourceReference:'a'.repeat(501)}])assert.ok(uploadFieldsError({...entry,...patch}))
  assert.equal(uploadFieldsError({...entry,rightsBasis:'LICENSED',sourceReference:'تفويض مكتوب'}),null)
})
test('upload uses FormData, stable token and no inherited JSON header or invented boundary',async()=>{
  let captured;const client={post:async(...args)=>{captured=args;return {data:manifest()}}}
  const file=new File(['test'],'fixture.jpg',{type:'image/jpeg'}), signal=new AbortController().signal
  const result=await createImagesApi(client,15).upload({file,token:id,rightsBasis:'OWNED',altText:null,sourceReference:null},signal)
  assert.equal(result.revision,'9007199254740993');assert.equal(captured[0],'/api/admin/properties/15/images')
  assert.ok(captured[1] instanceof FormData);assert.equal(captured[1].get('file').name,'fixture.jpg')
  assert.equal(captured[1].get('uploadToken'),id);assert.equal(captured[1].get('rightsConfirmed'),'true')
  assert.equal(captured[1].has('altText'),false);assert.equal(captured[2].headers['Content-Type'],undefined);assert.equal(captured[2].signal,signal)
})
test('DELETE uses query revision/replacement and handles cleanup pending without a body',async()=>{
  let captured;const client={delete:async(...args)=>{captured=args;return {status:202}}}
  assert.equal(await createImagesApi(client,15).remove(id,'9007199254740993',other),202)
  assert.deepEqual(captured[1].params,{expectedRevision:'9007199254740993',replacementPrimaryImageId:other});assert.equal(captured[1].data,undefined)
})
test('loader releases blobs and ignores a late canceled private response after viewer changes',async()=>{
  const first=deferred(), events=[],revoked=[], requests=[]
  const client={get:(path,config)=>{requests.push({path,config});return requests.length===1?first.promise:Promise.resolve({data:new Blob(['jpeg'],{type:'image/jpeg'}),headers:{'content-type':'image/jpeg'}})}}
  const loader=createImageLoader(client,(state)=>events.push(state),{createObjectURL:()=> 'blob:second',revokeObjectURL:(url)=>revoked.push(url)})
  const pending=loader.load('admin',imagePath(15,id,'thumbnail'),'thumbnail');loader.cancel()
  await loader.load('guest',imagePath(15,other,'thumbnail'),'thumbnail')
  first.resolve({data:new Blob(['private'],{type:'image/jpeg'}),headers:{'content-type':'image/jpeg'}});await pending
  assert.equal(events.at(-1).key,'guest');assert.equal(requests[0].config.signal.aborted,true);assert.equal(requests[1].config.responseType,'blob')
  loader.cancel();loader.cancel();assert.deepEqual(revoked,['blob:second'])
})
test('wrong type/empty/oversized response and 404/503 never produce object URLs',async()=>{
  for(const response of [{data:new Blob([],{type:'image/jpeg'}),headers:{'content-type':'image/jpeg'}},{data:new Blob(['json'],{type:'application/json'}),headers:{'content-type':'application/json'}},
    {data:new Blob([new Uint8Array(256*1024+1)],{type:'image/jpeg'}),headers:{'content-type':'image/jpeg'}}]){
    let state;const loader=createImageLoader({get:async()=>response},(next)=>state=next,{createObjectURL:()=>assert.fail('unsafe URL'),revokeObjectURL(){}})
    await loader.load('key','/safe','thumbnail');assert.equal(state.phase,'error')
  }
  for(const status of [404,503]){let state;const loader=createImageLoader({get:async()=>{throw {response:{status}}}},(next)=>state=next)
    await loader.load('key','/safe','display');assert.equal(state.phase,'error');assert.equal(state.url,null)}
})
test('server errors have Arabic user-facing messages',()=>{
  for(const status of [400,401,403,404,409,413,415,429,503,500])assert.match(imageError({response:{status}}),/[\u0600-\u06ff]/)
})
