import { recorder } from './cases.mjs'
import { parseDecimalInput, propertyDecimalRules, compareDecimals, formatDecimal } from '../../frontend/src/services/decimal.js'
import { propertyFormPayload, propertyFormState, propertyStatusError, blankProperty } from '../../frontend/src/services/adminProperty.js'
import { specificationFormState, specificationPayload, parseSpecificationIntegerInput } from '../../frontend/src/services/propertySpecifications.js'
import { blankPropertyNeeds, validatePropertyNeeds } from '../../frontend/src/services/propertyNeeds.js'
import { blankCatalogFilters, catalogSearch, readCatalogSearch } from '../../frontend/src/services/propertyCatalog.js'
import { readComparisonProperty, changeComparison, comparisonRows } from '../../frontend/src/services/propertyComparison.js'
import { readFavoritesPage } from '../../frontend/src/services/favorites.js'
import { readImageManifest, imagePath } from '../../frontend/src/services/propertyImages.js'
import { propertyContactActions } from '../../frontend/src/services/propertyContact.js'
import { resolveLocalDemoAdmin } from '../../frontend/src/services/localDemoAdmin.js'
import { createRequestGate } from '../../frontend/src/services/requestGate.js'

const r = recorder('frontend', process.argv[2]), check = r.check
const types = ['LAND','APARTMENT','VILLA','BUILDING','COMMERCIAL','FARM','OTHER']
const listings = [['SALE',null],['RENT','MONTHLY'],['RENT','YEARLY']]
const fields = ['price','area','streetWidth','latitude','longitude']
// Independent contract constants, not values derived from the implementation being tested.
const limits = {price:[17,2],area:[10,2],streetWidth:[8,2],latitude:[3,7],longitude:[3,7]}
function decimal(units, scale) {
  const negative=units<0n, digits=(negative?-units:units).toString().padStart(scale+1,'0')
  return `${negative?'-':''}${digits.slice(0,-scale)}.${digits.slice(-scale)}`
}
function script(value, alphabet) {
  if (!alphabet) return value
  const digits=alphabet===1?'٠١٢٣٤٥٦٧٨٩':'۰۱۲۳۴۵۶۷۸۹'
  return value.replace(/[0-9]/g,d=>digits[d]).replaceAll('.','٫')
}
function grouped(value) {
  const [whole,fraction]=value.split('.'), sign=whole.startsWith('-')?'-':''
  let remaining=whole.replace('-',''), parts=[]
  while(remaining.length>3){parts.unshift(remaining.slice(-3));remaining=remaining.slice(0,-3)}
  parts.unshift(remaining); return sign+parts.join('٬')+(fraction===undefined?'':`٫${fraction}`)
}
const summary = (id,type,listingType,rentalPeriod,i=0) => ({id:String(id),type,listingType,rentalPeriod,title:'Synthetic quality property',city:'مكة',district:'النوارية',status:'ACTIVE',price:`9007199254740993.${String(i).padStart(2,'0')}`,area:'500.25',streetWidth:null,specifications:{}})

for (const name of fields) for(let i=0;i<100;i++) for(let alphabet=0;alphabet<3;alphabet++) {
  const [integer,scale]=limits[name]
  const units=name==='latitude'||name==='longitude'?BigInt((i-50)*1234567):10n**BigInt(integer+scale)-1n-BigInt(i*7919)
  const expected=decimal(units,scale), raw=script(expected,alphabet)
  await check('decimal-valid',`${name}-${i}-${alphabet}`,{name,raw},expected,()=>parseDecimalInput(raw,propertyDecimalRules[name]).value)
}
for(const name of fields) for(let i=0;i<60;i++) for(let alphabet=0;alphabet<3;alphabet++) {
  const [integer,scale]=limits[name], signed=['latitude','longitude'].includes(name)
  const invalid=[`${i+1}.${String(i+1).padStart(scale+1,'0')}`,`1${'0'.repeat(integer)}.${i+1}`,`${i+1}e${1000+i}`,`${i+1},${i+2}`,`${i+1}..${i+2}`,signed?`${200+i}.01`:`-${i+1}.01`][i%6]
  const raw=script(invalid,alphabet)
  await check('decimal-invalid',`${name}-${i}-${alphabet}`,{name,raw},true,()=>Boolean(parseDecimalInput(raw,propertyDecimalRules[name]).error))
}
for(let i=0;i<600;i++) {
  const a=900719925474099301n+BigInt(i*7919), b=a+BigInt(i%3-1)
  const left=decimal(i%2?-a:a,2),right=decimal(i%2?-b:b,2)
  const x=i%2?-a:a,y=i%2?-b:b
  await check('decimal-compare',i,{left,right},x<y?-1:x>y?1:0,()=>compareDecimals(left,right))
  const raw=decimal(i%2?-a:a,2)
  await check('decimal-format',i,{raw},grouped(raw),()=>formatDecimal(raw))
}
for(const type of types) for(const [listingType,rentalPeriod] of listings) for(let i=0;i<20;i++) {
  const p={...blankProperty,...summary('1',type,listingType,rentalPeriod,i),latitude:i%2?'-21.1234567':null,longitude:i%2?'39.1234567':null,formattedAddress:i%2?'عنوان صناعي':null}
  const expected={price:p.price,area:p.area,latitude:p.latitude,longitude:p.longitude,rentalPeriod}
  await check('admin-roundtrip',`${type}-${listingType}-${rentalPeriod}-${i}`,{type,listingType,rentalPeriod,price:p.price,latitude:p.latitude},expected,()=>{
    const state=propertyFormState(p),result=propertyFormPayload(state.values,state.sourceErrors)
    return result.payload?Object.fromEntries(Object.keys(expected).map(k=>[k,result.payload[k]])):result.errors
  })
}
for(const type of types) for(const [listingType,rentalPeriod] of listings) for(const status of ['ACTIVE','DRAFT','INACTIVE','SOLD'])
  await check('admin-status',`${type}-${listingType}-${rentalPeriod}-${status}`,{type,listingType,rentalPeriod,status},!(listingType==='RENT'&&status==='SOLD'),()=>!propertyStatusError(listingType,status))
const counters={bedrooms:[0,100],bathrooms:[0,100],livingRooms:[0,100],floorNumber:[-10,200],floorCount:[1,200],totalUnits:[0,10000],residentialUnits:[0,10000],commercialUnits:[0,10000],parkingSpaces:[0,1000],estimatedAgeYears:[0,1000],ageAsOfYear:[1900,2026],wellCount:[0,100]}
for(const [name,[min,max]] of Object.entries(counters)) for(let i=0;i<25;i++) for(let alphabet=0;alphabet<3;alphabet++) {
  const value=i<10?min+i:i<20?max+1+(i-10):min-1-(i-20),raw=script(String(value),alphabet)
  await check('spec-integer',`${name}-${i}-${alphabet}`,{name,raw},value>=min&&value<=max?value:'rejected',()=>{const p=parseSpecificationIntegerInput(raw,name);return p.error?'rejected':p.value})
}
const booleanFields=['hasElevator','hasAirConditioning','hasFittedKitchen','hasMaidRoom','hasDriverRoom','hasPool','hasYard','hasBalcony','hasPrivateRoof','electricityConnected','waterConnected','sewerConnected','hasIrrigation','waterConnected','hasElevator']
// The two repeated names below use distinct applicable contexts, not duplicated scenarios.
for(let f=0;f<booleanFields.length;f++) for(let i=0;i<20;i++) {
  const name=booleanFields[f], type=f===12?'FARM':f===13?'LAND':f>=9&&f<=11?'BUILDING':f===14?'VILLA':'APARTMENT'
  const before={hasElevator:true,hasPool:false,parkingSpaces:i},present={...before,[name]:i%2===0}
  if(type==='FARM'||type==='LAND'){delete present.hasElevator;delete present.hasPool}
  if(type==='BUILDING'){delete present.hasPool}
  const property={type,specifications:present}, state=specificationFormState(property)
  state.values[name]=i%3===0?'':String(!(i%2===0))
  const expected=i%3===0?null:!(i%2===0)
  await check('spec-merge',`${f}-${i}`,{type,name,before:present,raw:state.values[name]},expected,()=>{const result=specificationPayload(state.values,{property,type,area:'500.25',year:2026});return result.patch?result.patch[name]:'invalid'})
}
for(const type of types) for(const [listingType,rentalPeriod] of listings) for(let i=0;i<20;i++) {
  const values={...blankPropertyNeeds(),type,listingType,rentalPeriod:rentalPeriod??'',maxBudget:`9007199254740993.${String(i).padStart(2,'0')}`,district:i%2?'حي النوارية':'',minArea:String(i+1),maxArea:String(i+501)}
  const key=`${type}-${listingType}-${rentalPeriod}-${i}`
  await check('needs-valid',key,values,{budget:values.maxBudget,type,listingType,rentalPeriod},()=>{const p=validatePropertyNeeds(values).payload;return p?{budget:p.maxBudget,type:p.type,listingType:p.listingType,rentalPeriod:p.rentalPeriod}:null})
  const invalid={...values,maxBudget:`${i+1}.001`}
  await check('needs-invalid',key,invalid,true,()=>Boolean(validatePropertyNeeds(invalid).errors.maxBudget))
}
for(const city of ['مكة','مكة المكرمة','Makkah']) for(const type of types) for(const [listingType,rentalPeriod] of listings) for(let i=0;i<10;i++) {
  const values={...blankCatalogFilters(),city,type,listingType,rentalPeriod:rentalPeriod??'',district:`حي اختبار ${i}`,minPrice:String(i),maxPrice:String(i+100)}
  await check('catalog-url',`${city}-${type}-${listingType}-${rentalPeriod}-${i}`,values,{city:'مكة المكرمة',type,listingType,rentalPeriod:rentalPeriod??'',minPrice:String(i),page:i},()=>{
    const p=readCatalogSearch(catalogSearch(values,i));return p.params?{city:p.params.city,type:p.params.type,listingType:p.params.listingType,rentalPeriod:p.params.rentalPeriod??'',minPrice:p.params.minPrice,page:p.page}:p.errors
  })
}
for(const type of types) for(const [listingType,rentalPeriod] of listings) for(let i=0;i<20;i++) {
  const p=summary(String(i+1),type,listingType,rentalPeriod,i)
  await check('comparison-data',`${type}-${listingType}-${rentalPeriod}-${i}`,p,{price:grouped(p.price),unit:listingType==='SALE'?'ر.س — بيع':rentalPeriod==='MONTHLY'?'ر.س / شهر':'ر.س / سنة'},()=>{
    const safe=readComparisonProperty(p,p.id),row=comparisonRows([{phase:'ready',id:p.id,property:safe}]).find(x=>x.name==='price').cells[0]
    return {price:row.text,unit:row.unit}
  })
}
for(const type of types) for(const [listingType,rentalPeriod] of listings) for(let i=0;i<10;i++) {
  const p=summary(String(1+i+100*types.indexOf(type)+10*listings.findIndex(v=>v[0]===listingType&&v[1]===rentalPeriod)),type,listingType,rentalPeriod,i),hidden=i%2===1
  const raw={content:[{propertyId:p.id,createdAt:'2026-10-25T00:00:00Z',available:!hidden,property:hidden?null:p}],page:0,size:12,totalElements:1,totalPages:1,first:true,last:true}
  await check('favorites-privacy',`${type}-${listingType}-${rentalPeriod}-${i}`,raw,hidden?null:p.price,()=>readFavoritesPage(raw).content[0].property?.price??null)
}
for(let n=1;n<=12;n++) for(let variant=0;variant<20;variant++) {
  const propertyId=String(variant+1),images=Array.from({length:n},(_,i)=>{
    const id=`00000000-0000-4000-8000-${String(i+1).padStart(12,'0')}`
    return {id,order:i,altText:`صورة ${variant}-${i}`,thumbnail:{url:imagePath(propertyId,id,'thumbnail'),width:640,height:480},display:{url:imagePath(propertyId,id,'display'),width:1200,height:900}}
  })
  const manifest={revision:String(variant),primaryImageId:images[n-1].id,images}
  if(variant%2)manifest.images[n-1].order=n // invalid complete manifest ordering
  await check('image-manifest',`${n}-${variant}`,{propertyId,manifest},variant%2===0,()=>{try{readImageManifest(manifest,propertyId);return true}catch{return false}})
}
const origins=['https://example.invalid','http://localhost:5173','javascript:alert(1)'],phones=['+966500032165','+٩٦٦٥٠٠٠٣٢١٦٥','not-a-phone','+123']
for(const type of types) for(const status of ['ACTIVE','SOLD','DRAFT','INACTIVE']) for(const origin of origins) for(const phone of phones) {
  const good=status==='ACTIVE'&&origin!=='javascript:alert(1)'&&phones.indexOf(phone)<2
  await check('contact-policy',`${type}-${status}-${origins.indexOf(origin)}-${phones.indexOf(phone)}`,{type,status,origin,phone},good?2:0,()=>propertyContactActions({id:1,type,status},{phone,whatsapp:phone},origin,true).length)
}
for(let i=0;i<120;i++) {
  const beginCount=2+Math.floor(i/2),cancel=i%2===0
  await check('request-gate',i,{beginCount,cancel},true,()=>{
    const gate=createRequestGate(),tickets=Array.from({length:beginCount},()=>gate.begin())
    if(cancel)gate.cancel()
    return tickets.every((t,k)=>gate.isCurrent(t)===(!cancel&&k===beginCount-1))&&tickets.slice(0,-1).every(t=>t.controller.signal.aborted)
  })
}
for(let i=0;i<300;i++) {
  const development=i%3!==0,hostname=i%5===0?`localhost.evil${i}.invalid`:'localhost',apiBaseUrl=i%7===0?`https://api${i}.invalid`:`http://127.0.0.1:${8000+i}`
  const input={development,hostname,apiBaseUrl,enabled:'true',email:'synthetic@example.invalid',password:'Synthetic-Only!'}
  await check('local-demo-guard',i,input,development&&hostname==='localhost'&&apiBaseUrl.startsWith('http://127.0.0.1:'),()=>Boolean(resolveLocalDemoAdmin(input)))
}
for(let i=0;i<600;i++) {
  const ids=[String(i*4+1),String(i*4+2),String(i*4+3),String(i*4+4)]
  await check('comparison-sequence',i,{ids,removeIndex:i%3},[...ids.slice(0,3).filter((_,k)=>k!==i%3),ids[3]],()=>{
    let s={ids:[],notice:''};for(const id of ids)s=changeComparison(s,'add',id)
    s=changeComparison(s,'remove',ids[i%3]);s=changeComparison(s,'add',ids[3]);return s.ids
  })
}
const totals=await r.finish(9000);process.exitCode=totals.failed?2:0
