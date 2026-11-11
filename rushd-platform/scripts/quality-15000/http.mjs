import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
import { recorder } from './cases.mjs'
import { verifyOwnedServer } from './ownership.mjs'

// Deliberately not configurable: run.sh owns this loopback process and its disposable DB.
const base='http://127.0.0.1:18096', work=process.argv[3], password='Quality-Synthetic-Only!'
if(!['seed','run'].includes(process.argv[2])||!work)throw new Error('Use the owned run.sh launcher')
await verifyOwnedServer(work) // Refuse direct execution against an existing unowned server.
function client(name){
  const jar=new Map();let token=null
  const c={name,jar,saved:new Set(),async request(path,method='GET',body,options={}){
    if(!['GET','HEAD','OPTIONS'].includes(method)&&!options.noCsrf&&!token)token=(await c.request('/api/auth/csrf')).data.token
    const multipart=body instanceof FormData
    const response=await fetch(base+path,{method,redirect:'error',signal:AbortSignal.timeout(15000),headers:{
      Cookie:[...jar].map(([k,v])=>`${k}=${v}`).join('; '),...(body!==undefined&&!multipart?{'Content-Type':'application/json'}:{}),
      ...(!options.noCsrf&&token?{'X-XSRF-TOKEN':token}:{}),...options.headers,
    },...(body===undefined?{}:{body:multipart?body:options.raw?body:JSON.stringify(body)})})
    for(const cookie of response.headers.getSetCookie()){
      const [key,...values]=cookie.split(';')[0].split('=');const value=values.join('=');if(value)jar.set(key,value);else jar.delete(key)
    }
    const contentType=response.headers.get('content-type')??'',text=await response.text();let data
    try{data=JSON.parse(text)}catch{data=text}
    if(response.ok&&['/api/auth/login','/api/auth/logout','/api/auth/refresh'].includes(path))token=null
    return {status:response.status,data,contentType}
  },async login(){const p=await c.request('/api/auth/login','POST',{email:`quality-${name}@example.invalid`,password});assert.equal(p.status,200,`Fixture login ${name}: ${p.status}`)}}
  return c
}
const accounts=['admin','buyer','other','seller']
if(process.argv[2]==='seed'){
  const guest=client('seed')
  for(const name of accounts){const response=await guest.request('/api/auth/register','POST',{name:`Synthetic ${name}`,email:`quality-${name}@example.invalid`,password,role:'BUYER'});assert.equal(response.status,201,`Seed ${name}: ${response.status}`)}
  console.log('Four synthetic BUYER fixtures created; role promotion is guarded by the owned launcher.');process.exit(0)
}
const r=recorder('http',`${work}/http.jsonl`),check=r.check
const actors={guest:client('guest'),admin:client('admin'),buyer:client('buyer'),other:client('other'),seller:client('seller')}
for(const name of accounts)await actors[name].login()
const admin=actors.admin,types=['LAND','APARTMENT','VILLA','BUILDING','COMMERCIAL','FARM','OTHER'],listings=[['SALE',null],['RENT','MONTHLY'],['RENT','YEARLY']]
const model=new Map(),fixtures={}
const cents=s=>{const [a,b='']=s.split('.');return BigInt(a)*100n+BigInt(b.padEnd(2,'0'))}
const fixed=(units,scale=2)=>{const sign=units<0n?'-':'';const s=(units<0n?-units:units).toString().padStart(scale+1,'0');return `${sign}${s.slice(0,-scale)}.${s.slice(-scale)}`}
const body=(type='LAND',extra={})=>({title:'Synthetic quality property',type,listingType:'SALE',rentalPeriod:null,city:'مكة',district:'النوارية',price:'100.01',area:'500.25',purpose:'RESIDENTIAL',status:'ACTIVE',...extra})
async function create(payload){const response=await admin.request('/api/properties','POST',payload);assert.equal(response.status,201,`Fixture creation failed: ${response.status}`);model.set(response.data.id,response.data);return response.data}
function key(type,kind){return `${type}-${kind[0]}-${kind[1]}`}
for(const [t,type] of types.entries()){
  fixtures[type]={public:[],private:await create(body(type,{status:'DRAFT',title:`Synthetic private ${type}`})),sold:await create(body(type,{status:'SOLD',title:`Synthetic sold ${type}`}))}
  for(const [l,kind]of listings.entries())for(let variant=0;variant<2;variant++){
    const p=await create(body(type,{title:`Synthetic ${type} ${kind.join('-')} ${variant}`,listingType:kind[0],rentalPeriod:kind[1],price:fixed(BigInt((1000+t*100+l*10)*100+1+variant*10)),area:fixed(BigInt((100+t*10+l)*100)),specifications:{parkingSpaces:l+variant}}))
    fixtures[type].public.push(p);fixtures[key(type,kind)]??=[];fixtures[key(type,kind)].push(p)
  }
}
// 280 scenarios; each scenario is a whole create -> update -> independent GET roundtrip.
for(const [t,type] of types.entries())for(let i=0;i<40;i++){
  const kind=listings[i%3],price=fixed(900719925474099301n+BigInt(t*100+i)),area=fixed(BigInt(10001+i)),latitude=i%2?fixed(-210000000n+BigInt(i),7):null
  const payload=body(type,{listingType:kind[0],rentalPeriod:kind[1],price,area,latitude,longitude:i%2?'39.8262000':null,googlePlaceId:i%2?`synthetic-${t}-${i}`:null,formattedAddress:i%2?'عنوان صناعي':null,neighborhood:i%2?'النوارية':null,status:kind[0]==='RENT'&&i%4===3?'ACTIVE':['ACTIVE','DRAFT','INACTIVE','SOLD'][i%4],city:['مكة','مكة المكرمة','Makkah'][i%3]})
  await check('property-roundtrip',`${type}-${i}`,payload,{price:fixed(cents(price)+1n),area,latitude,longitude:payload.longitude,googlePlaceId:payload.googlePlaceId},async()=>{
    const created=await admin.request('/api/properties','POST',payload);assert.equal(created.status,201)
    const updated=await admin.request(`/api/properties/${created.data.id}`,'PUT',{...payload,price:fixed(cents(price)+1n)});assert.equal(updated.status,200)
    const read=await admin.request(`/api/properties/${created.data.id}`);assert.equal(read.status,200);model.set(read.data.id,read.data)
    return Object.fromEntries(['price','area','latitude','longitude','googlePlaceId'].map(k=>[k,read.data[k]]))
  })
}
for(const type of types)for(let i=0;i<100;i++){
  const payload=body(type),kind=i%10,updating=i%2===1,path=updating?`/api/properties/${fixtures[type].public[0].id}`:'/api/properties'
  if(kind===0)payload.price=`${i+1}.001`
  if(kind===1)payload.area=`10000000000.${String(i).padStart(2,'0')}`
  if(kind===2)payload.latitude=String(91+i)
  if(kind===3)payload.longitude=String(-181-i)
  if(kind===4)payload.title='ع'.repeat(151+i)
  if(kind===5){payload.listingType='RENT';payload.rentalPeriod=`INVALID_${i}`}
  if(kind===6)payload.specifications={[`unsupported_${i}`]:true}
  if(kind===7)payload.streetWidth=`-${i+1}.01`
  if(kind===8)payload.googlePlaceId='x'.repeat(256+i)
  if(kind===9)payload.purpose=`INVALID_${i}`
  await check('property-validation',`${type}-${i}`,{method:updating?'PUT':'POST',path,payload},400,async()=>{
    const response=await admin.request(path,updating?'PUT':'POST',payload)
    if(updating){const reread=await admin.request(path);assert.equal(reread.data.price,model.get(fixtures[type].public[0].id).price,'Rejected update must not mutate stored price')}
    return response.status
  })
}
for(const type of types)for(const role of ['guest','buyer','seller','admin'])for(let operation=0;operation<10;operation++){
  const actor=actors[role],p=fixtures[type].public[0],hidden=fixtures[type].private
  const jobs=[
    ['GET',`/api/properties/${p.id}`,undefined,200],['GET',`/api/properties/${hidden.id}`,undefined,role==='admin'?200:404],
    ['GET','/api/admin/properties',undefined,role==='admin'?200:role==='guest'?401:403],
    ['POST','/api/properties',body(type,{title:`Synthetic role ${role} ${type}`}),role==='admin'?201:role==='guest'?401:403],
    ['PUT',`/api/properties/${p.id}`,body(type,{price:p.price,area:p.area}),role==='admin'?200:role==='guest'?401:403],
    ['POST','/api/property-needs/validate',{listingType:'SALE',type,maxBudget:'999999.99'},role==='guest'?401:200],
    ['GET','/api/me/favorites',undefined,role==='guest'?401:200],
    ['PUT',`/api/me/favorites/${p.id}`,undefined,role==='guest'?401:204],
    ['GET',`/api/properties/${p.id}/images`,undefined,200],
    ['PUT',`/api/admin/properties/${p.id}/images`,{expectedRevision:'0',primaryImageId:null,images:[]},role==='admin'?200:role==='guest'?401:403],
  ]
  const [method,path,payload,expected]=jobs[operation]
  await check('authorization',`${type}-${role}-${operation}`,{type,role,method,path,payload},expected,async()=>{
    const response=await actor.request(path,method,payload)
    if(role==='admin'&&[3,4].includes(operation)&&[200,201].includes(response.status))model.set(response.data.id,response.data)
    if(operation===7&&response.status===204)actor.saved.add(String(p.id))
    return response.status
  })
}
for(const type of types)for(const kind of listings)for(let i=0;i<10;i++){
  const params=new URLSearchParams({city:['مكة','مكة المكرمة','Makkah'][i%3],type,listingType:kind[0],page:String(i===9?1:0),size:String(i+1),sort:i%2?'price,asc':'createdAt,desc'})
  if(kind[1])params.set('rentalPeriod',kind[1]);const minimum=i%3===0?'9007199254740993.02':String(i);params.set('minPrice',minimum)
  const values=[...model.values()].filter(p=>p.status==='ACTIVE'&&p.type===type&&p.listingType===kind[0]&&(!kind[1]||p.rentalPeriod===kind[1])&&cents(p.price)>=cents(minimum))
  values.sort((a,b)=>{if(i%2){const x=cents(a.price),y=cents(b.price);return x<y?-1:x>y?1:b.id-a.id}return b.createdAt.localeCompare(a.createdAt)||b.id-a.id})
  const page=i===9?1:0,size=i+1,expected={total:values.length,ids:values.slice(page*size,(page+1)*size).map(p=>p.id)}
  await check('catalog-query',`${type}-${kind.join('-')}-${i}`,{query:params.toString()},expected,async()=>{const response=await actors.guest.request(`/api/properties?${params}`);assert.equal(response.status,200);return {total:response.data.totalElements,ids:response.data.content.map(p=>p.id)}})
}
for(const [t,type]of types.entries())for(const [l,kind]of listings.entries())for(let i=0;i<10;i++){
  const maxBudget=`${1000+t*100+l*10+i}.00`,target=i%3
  const payload={type,listingType:kind[0],rentalPeriod:kind[1],maxBudget,minArea:String(50+i),maxArea:String(180+i),areaRequirement:'REQUIRED',specificationCriteria:{parkingSpaces:{min:target,max:target,requirement:i%2?'PREFERRED':'REQUIRED'}}}
  if(i%3!==2){payload.district=i%3===0?'حي النوارية':'حي الياسمين';payload.districtRequirement=i%3===0?'REQUIRED':'PREFERRED'}
  const possible=[...model.values()].filter(p=>p.status==='ACTIVE'&&p.type===type&&p.listingType===kind[0]&&p.rentalPeriod===kind[1]&&cents(p.price)>0n&&cents(p.price)<=cents(maxBudget)&&cents(p.area)>=cents(payload.minArea)&&cents(p.area)<=cents(payload.maxArea))
    .filter(p=>i%2||p.specifications?.parkingSpaces===target)
  const preference=p=>(i%2&&p.specifications?.parkingSpaces===target?1:0) // preferred Yasmin does not match these fixtures
  possible.sort((a,b)=>preference(b)-preference(a)||b.createdAt.localeCompare(a.createdAt)||b.id-a.id)
  const expected=possible.map(p=>({id:p.id,matched:preference(p)}))
  await check('matching',`${type}-${kind.join('-')}-${i}`,payload,expected,async()=>{const response=await actors.buyer.request('/api/property-needs/matches?size=20','POST',payload);assert.equal(response.status,200);return response.data.content.map(p=>({id:p.id,matched:p.preferredMatchedCount}))})
}
for(const type of types)for(let i=0;i<20;i++){
  const actor=i<10?actors.buyer:actors.other,other=i<10?actors.other:actors.buyer
  const p=[fixtures[type].public[0],fixtures[type].public[1],fixtures[type].public[2],fixtures[type].sold,fixtures[type].private][Math.floor((i%10)/2)]
  const id=String(p.id),remove=i%2===0
  const expectedStatus=remove?204:p.status==='DRAFT'?404:204,expectedSaved=remove?false:expectedStatus===204?true:actor.saved.has(id),otherSaved=other.saved.has(id)
  await check('favorites-ownership',`${type}-${i}`,{actor:actor.name,id,remove,before:actor.saved.has(id),otherBefore:otherSaved}, {status:expectedStatus,saved:expectedSaved,otherSaved},async()=>{
    const method=remove?'DELETE':'PUT',path=`/api/me/favorites/${id}`,first=await actor.request(path,method),second=await actor.request(path,method);assert.equal(first.status,second.status,'Idempotent retry status differs')
    const own=await actor.request(`/api/me/favorites/status?propertyIds=${id}`),foreign=await other.request(`/api/me/favorites/status?propertyIds=${id}`)
    assert.equal(own.status,200);assert.equal(foreign.status,200)
    if(first.status===204){if(remove)actor.saved.delete(id);else actor.saved.add(id)}
    return {status:first.status,saved:own.data.favoritePropertyIds.includes(id),otherSaved:foreign.data.favoritePropertyIds.includes(id)}
  })
}
const jpeg=await readFile(`${work}/synthetic.jpg`)
for(const type of types){
  const property=await create(body(type,{title:`Synthetic image lifecycle ${type}`})),path=`/api/admin/properties/${property.id}/images`,publicPath=`/api/properties/${property.id}/images`
  const tokens=[randomUUID(),randomUUID()];let manifest={revision:'0',images:[],primaryImageId:null},a,b
  const upload=async(index,extra={})=>{const form=new FormData();form.append('file',new Blob([extra.bytes??jpeg],{type:extra.mime??'image/jpeg'}),extra.filename??'synthetic.jpg');form.append('uploadToken',tokens[index]??randomUUID());form.append('rightsConfirmed',extra.confirmed??'true');form.append('rightsBasis','OWNED');form.append('altText',`Synthetic ${index}`);return admin.request(path,'POST',form)}
  const currentBody=()=>({expectedRevision:manifest.revision,primaryImageId:manifest.primaryImageId,images:manifest.images.map(x=>({id:x.id,altText:x.altText}))})
  for(let i=0;i<20;i++){
    const expected=[201,200,201,200,200,409,400,400,400,400,204,404,200,415,413,415,400,400,204,200][i]
    await check('image-lifecycle',`${type}-${i}`,{propertyId:property.id,step:i,revision:manifest.revision,description:['upload','retry','second-upload','read-two','reorder-primary','stale-revision','unknown-id','incomplete-manifest','bad-primary','delete-needs-replacement','delete-with-replacement','deleted-content-private','read-one','mime-mismatch','oversize','active-svg','traversal-name','missing-rights','delete-last','read-empty'][i]},expected,async()=>{
      let response
      if(i===0){response=await upload(0);if(response.status===201){manifest=response.data;a=manifest.images[0].id}}
      if(i===1)response=await upload(0)
      if(i===2){response=await upload(1);if(response.status===201){manifest=response.data;b=manifest.images.find(x=>x.id!==a).id}}
      if(i===3){response=await actors.guest.request(publicPath);if(response.status===200)assert.equal(response.data.images.length,2)}
      if(i===4){const payload=currentBody();payload.images.reverse();payload.primaryImageId=b;response=await admin.request(path,'PUT',payload);if(response.status===200)manifest=response.data}
      if(i===5)response=await admin.request(path,'PUT',{...currentBody(),expectedRevision:'0'})
      if(i===6)response=await admin.request(path,'PUT',{...currentBody(),images:[...currentBody().images,{id:randomUUID(),altText:null}]})
      if(i===7)response=await admin.request(path,'PUT',{...currentBody(),images:[]})
      if(i===8)response=await admin.request(path,'PUT',{...currentBody(),primaryImageId:randomUUID()})
      if(i===9)response=await admin.request(`${path}/${b}?expectedRevision=${manifest.revision}`,'DELETE')
      if(i===10){response=await admin.request(`${path}/${b}?expectedRevision=${manifest.revision}&replacementPrimaryImageId=${a}`,'DELETE');manifest=(await admin.request(publicPath)).data}
      if(i===11)response=await actors.guest.request(`${publicPath}/${b}/display`)
      if(i===12){response=await actors.guest.request(publicPath);if(response.status===200)assert.equal(response.data.images.length,1)}
      if(i===13)response=await upload(2,{mime:'image/png',filename:'synthetic.png'})
      if(i===14)response=await upload(2,{bytes:Buffer.alloc(8*1024*1024+1)})
      if(i===15)response=await upload(2,{mime:'image/svg+xml',filename:'active.svg',bytes:Buffer.from('<svg><script>not executed</script></svg>')})
      if(i===16)response=await upload(2,{filename:'../synthetic.jpg'})
      if(i===17)response=await upload(2,{confirmed:'false'})
      if(i===18)response=await admin.request(`${path}/${a}?expectedRevision=${manifest.revision}`,'DELETE')
      if(i===19){response=await actors.guest.request(publicPath);if(response.status===200)assert.equal(response.data.images.length,0)}
      return response.status
    })
  }
}
// 40 targeted auth/protocol regressions use desired behavior, including known audit defects.
// Setup already logs four actors in. Respect the unchanged 10/minute policy
// before this phase (including newly approved successful boundary logins).
// This is pacing, not a retry counted as another case or an accepted 429.
await new Promise(resolve => setTimeout(resolve, 60_000))
const lengths=[0,1,2,254,255,256,257,300,1000,10]
for(let i=0;i<20;i++){
  const length=lengths[i%10],name=(i<10?'N':'ع').repeat(length),expected=length===0||length>255?400:201
  await check('auth-registration-length',i,{alphabet:i<10?'ASCII':'Arabic',length},expected,async()=> (await actors.guest.request('/api/auth/register','POST',{name,email:`quality-length-${i}@example.invalid`,password,role:'BUYER'})).status)
}
for(const [i,role]of ['ADMIN','SELLER',0,2].entries())await check('auth-role',i,{role},400,async()=>(await actors.guest.request('/api/auth/register','POST',{name:'Synthetic role test',email:`quality-role-${i}@example.invalid`,password,role})).status)
for(const [i,[path,method,raw,expected]]of [['/api/auth/login','GET',false,405],['/api/auth/register','GET',false,405],['/api/auth/register','POST',true,415],['/api/auth/login','POST',true,415]].entries())
  await check('auth-protocol',i,{path,method,contentType:raw?'text/plain':null},expected,async()=>(await actors.guest.request(path,method,raw?'invalid-body':undefined,raw?{raw:true,headers:{'Content-Type':'text/plain'}}:{})).status)
for(let i=0;i<2;i++)await check('auth-duplicate-race',i,{email:`quality-race-${i}@example.invalid`},[201,409],async()=>{
  const b={name:'Synthetic duplicate',email:`quality-race-${i}@example.invalid`,password,role:'BUYER'}
  return (await Promise.all([actors.guest.request('/api/auth/register','POST',b),actors.guest.request('/api/auth/register','POST',b)])).map(p=>p.status).sort()
})
for(let i=0;i<2;i++)await check('auth-password-distinct',i,{alphabet:i?'Arabic':'ASCII',mutatedAfterBytes:72},401,async()=>{
  const c=client('long-'+i),prefix=i?'ع'.repeat(36):'A'.repeat(72),email=`quality-long-${i}@example.invalid`
  // Approved contract: verify explicit rejection, not a blanket exception-as-pass.
  const rejected=await c.request('/api/auth/register','POST',{name:'Synthetic long password',email,password:prefix+'-original',role:'BUYER'});assert.equal(rejected.status,400)
  const registered=await c.request('/api/auth/register','POST',{name:'Synthetic boundary password',email,password:prefix,role:'BUYER'});assert.equal(registered.status,201)
  const wrong=await c.request('/api/auth/login','POST',{email,password:prefix+'-different'})
  assert.equal((await c.request('/api/auth/login','POST',{email,password:prefix})).status,200)
  return wrong.status
})
for(let i=0;i<2;i++)await check('auth-no-csrf',i,{email:`no-csrf-${i}@example.invalid`},403,async()=>(await client('no-csrf').request('/api/auth/login','POST',{email:`no-csrf-${i}@example.invalid`,password},{noCsrf:true})).status)
for(let i=0;i<2;i++)await check('auth-wrong-csrf',i,{header:`wrong-${i}`},403,async()=>(await actors.guest.request('/api/auth/login','POST',{email:'quality-buyer@example.invalid',password},{headers:{'X-XSRF-TOKEN':`wrong-${i}`}})).status)
for(let i=0;i<2;i++)await check('auth-wrong-password',i,{email:'quality-buyer@example.invalid',suffix:i},401,async()=>(await actors.guest.request('/api/auth/login','POST',{email:'quality-buyer@example.invalid',password:`wrong-${i}`})).status)
for(let i=0;i<2;i++)await check('auth-invalid-jwt',i,{credentialType:i?'malformed-jwt':'opaque-not-access'},401,async()=>{const c=client('bad-jwt');c.jar.set('rawafed_access',i?'abc.def.ghi':'r1.'+'A'.repeat(43));return(await c.request('/api/auth/me')).status})
const totals=await r.finish(2000);process.exitCode=totals.failed?2:0
