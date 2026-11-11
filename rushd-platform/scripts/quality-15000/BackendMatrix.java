import com.fasterxml.jackson.databind.*;
import com.rushd.config.*;
import com.rushd.dto.*;
import com.rushd.entity.*;
import com.rushd.mapper.PropertyMapper;
import com.rushd.security.AuthRateLimiter;
import com.rushd.service.*;
import com.rushd.validation.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import com.rushd.security.BoundedBCryptPasswordEncoder;
import java.math.*;
import java.nio.file.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Standalone isolated audit matrix. It never opens a database/network connection. */
public class BackendMatrix {
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    static final Validator VALIDATOR = FACTORY.getValidator();
    static final Set<String> IDS = new HashSet<>(), SCENARIOS = new HashSet<>();
    static final Map<String,int[]> GROUPS = new LinkedHashMap<>();
    static PrintWriter out;
    static int total, failed;
    static final String SECRET = "quality-isolated-only-signing-key-with-at-least-32-bytes";
    static Map<String,Object> map(Object... pairs) {
        Map<String,Object> m = new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]); return m;
    }
    static void check(String group, Object key, Object input, Object expected, Callable<Object> action) throws Exception {
        String id="backend."+group+"."+key;
        if(!IDS.add(id)||!SCENARIOS.add(JSON.writeValueAsString(List.of(group,input,expected))))
            throw new IllegalStateException("Duplicate case/scenario: "+id);
        Object actual=null;String error=null;
        try {actual=action.call();if(!JSON.valueToTree(expected).equals(JSON.valueToTree(actual)))error="Expected "+expected+" but got "+actual;}
        catch(Throwable e){error=e.getClass().getSimpleName()+": "+e.getMessage();}
        total++;if(error!=null)failed++;
        int[] counts=GROUPS.computeIfAbsent(group,k->new int[2]);counts[0]++;if(error!=null)counts[1]++;
        out.println(JSON.writeValueAsString(map("id",id,"layer","backend","group",group,"input",input,"expected",expected,"actual",actual,"status",error==null?"passed":"failed","error",error)));
    }
    static Map<String,Object> body() {return map("title","Synthetic quality property","type","LAND","listingType","SALE","city","مكة","district","النوارية","price","100.01","area","500.25","status","ACTIVE","purpose","RESIDENTIAL");}
    static String fixed(BigInteger units,int scale){return new BigDecimal(units,scale).toPlainString();}
    static void set(PropertySpecifications s,String name,Object value,Class<?> cls)throws Exception{
        PropertySpecifications.class.getMethod("set"+Character.toUpperCase(name.charAt(0))+name.substring(1),cls).invoke(s,value);
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Expected new output JSONL and owned output directory");
        out=new PrintWriter(Files.newBufferedWriter(Path.of(args[0]),StandardOpenOption.CREATE_NEW));
        try {
            String[] fields={"price","area","streetWidth","latitude","longitude"};
            int[] integer={17,10,8,3,3}, scales={2,2,2,7,7};
            for(int f=0;f<5;f++)for(int i=0;i<200;i++){
                String field=fields[f],value;boolean signed=f>=3,valid;
                if(i==0){value=null;valid=f>=2;}
                else if(i==1){value="0";valid=signed;}
                else if(i<80){
                    BigInteger units=signed?BigInteger.valueOf((long)(i-40)*1234567):BigInteger.TEN.pow(integer[f]+scales[f]).subtract(BigInteger.valueOf(1L+i*7919L));
                    value=fixed(units,scales[f]);valid=true;
                } else if(i<120){value=signed?Integer.toString((f==3?91:181)+i):"1"+"0".repeat(integer[f])+"."+i;valid=false;}
                else if(i<160){value=(i+1)+"."+String.format("%0"+(scales[f]+1)+"d",i);valid=false;}
                else if(i<180){value="1e"+(10000+i);valid=false;}
                else {value=signed?"-"+((f==3?91:181)+i):"-"+(i+1)+".01";valid=false;}
                Map<String,Object> b=body();b.put(field,value);boolean update=i%2==1;
                check("dto-decimal",field+"-"+i,map("operation",update?"update":"create","field",field,"value",value),valid,()->{
                    Object dto=update?JSON.convertValue(b,UpdatePropertyRequest.class):JSON.convertValue(b,CreatePropertyRequest.class);
                    return VALIDATOR.validate(dto).isEmpty();
                });
            }
            Map<String,int[]> bounds=new LinkedHashMap<>();
            bounds.put("bedrooms",new int[]{0,100});bounds.put("bathrooms",new int[]{0,100});bounds.put("livingRooms",new int[]{0,100});
            bounds.put("floorNumber",new int[]{-10,200});bounds.put("floorCount",new int[]{1,200});bounds.put("totalUnits",new int[]{0,10000});
            bounds.put("residentialUnits",new int[]{0,10000});bounds.put("commercialUnits",new int[]{0,10000});bounds.put("parkingSpaces",new int[]{0,1000});
            bounds.put("estimatedAgeYears",new int[]{0,1000});bounds.put("ageAsOfYear",new int[]{1900,2026});bounds.put("wellCount",new int[]{0,100});
            for(var entry:bounds.entrySet())for(int i=0;i<50;i++){
                String name=entry.getKey();int min=entry.getValue()[0],max=entry.getValue()[1],value=i<25?min+i:max+1+i-25;
                check("spec-counter",name+"-"+i,map("field",name,"value",value),value>=min&&value<=max,()->{
                    PropertySpecifications s=new PropertySpecifications();set(s,name,value,Integer.class);
                    if(name.equals("estimatedAgeYears"))s.setAgeAsOfYear(2026);
                    if(name.equals("ageAsOfYear"))s.setEstimatedAgeYears(5);
                    return !PropertySpecificationsRules.valueErrors(s,Set.of("estimatedAgeYears","ageAsOfYear")).containsKey(name);
                });
            }
            for(String field:List.of("landArea","unitNetArea","unitGrossArea","totalBuiltArea"))for(int i=0;i<100;i++){
                String value=i<50?fixed(new BigInteger("999999999999").subtract(BigInteger.valueOf(i)),2):i<75?(i+1)+".001":(-i)+".01";
                check("spec-area",field+"-"+i,map("field",field,"value",value),i<50,()->{
                    var s=new PropertySpecifications();set(s,field,new BigDecimal(value),BigDecimal.class);
                    return !PropertySpecificationsRules.valueErrors(s,Set.of()).containsKey(field);
                });
            }
            for(int i=0;i<500;i++){
                int sample=i;String price=fixed(new BigInteger("900719925474099301").add(BigInteger.valueOf(i)),2);
                String area=fixed(BigInteger.valueOf(50025L+i),2);PropertyType type=PropertyType.values()[i%7];
                check("mapper-json",i,map("price",price,"area",area,"type",type.name(),"location",i%2==0),map("price",price,"area",area,"latitude",i%2==0?"-21.4225000":null,"googlePlaceId",i%2==0?"synthetic-place-"+i:null),()->{
                    Map<String,Object> b=body();b.put("price",price);b.put("area",area);b.put("type",type.name());
                    if(sample%2==0){b.put("latitude","-21.4225000");b.put("googlePlaceId","synthetic-place-"+sample);}
                    Property p=new Property();User publisher=new User();publisher.setId(1L);publisher.setName("Synthetic admin");publisher.setRole(Role.ADMIN);p.setPublisher(publisher);
                    PropertyMapper.applyCreateRequest(p,JSON.convertValue(b,CreatePropertyRequest.class));
                    JsonNode result=JSON.valueToTree(PropertyMapper.toResponse(p));
                    return map("price",result.get("price").textValue(),"area",result.get("area").textValue(),"latitude",result.get("latitude").isNull()?null:result.get("latitude").textValue(),"googlePlaceId",result.get("googlePlaceId").isNull()?null:result.get("googlePlaceId").textValue());
                });
            }
            for(int min=0;min<25;min++)for(int width=0;width<30;width++){
                int low=min,high=min+width,actual=(min*7+width*3)%101;boolean unknown=width%5==0,applicable=width%7!=0;
                String expected=unknown||!applicable?"UNKNOWN":actual>=low&&actual<=high?"MATCHED":"MISMATCHED";
                check("matching-evidence",min+"-"+width,map("min",low,"max",high,"actual",unknown?null:actual,"type",applicable?"APARTMENT":"LAND"),expected,()->{
                    Property p=new Property();p.setType(applicable?PropertyType.APARTMENT:PropertyType.LAND);
                    PropertySpecifications s=new PropertySpecifications();if(!unknown)s.setBedrooms(actual);p.setSpecifications(s);
                    return PropertyMatchingFields.match(p,"bedrooms",new PropertySpecificationCriterion(low,high,null,PropertyNeedsRequirement.REQUIRED)).name();
                });
            }
            String[] spaces={" ","\t","\n","\r","\u00a0","\u2002","\u2009","\u202f","\u205f","\u3000"};
            for(String city:List.of("مكة","مكة المكرمة","MaKkAh"))for(int a=0;a<10;a++)for(int b=0;b<10;b++){
                String raw=spaces[a]+city+spaces[b];
                check("makkah-alias",city+"-"+a+"-"+b,map("raw",raw),true,()->PropertyLocationComparison.isMakkah(raw));
            }
            for(String name:List.of("النوارية","الياسمين","زهرة العمرة","حياد","أم الجود"))for(int i=0;i<10;i++){
                String raw=spaces[i]+"حي"+spaces[i]+name+spaces[i];
                check("district-key",name+"-"+i,map("raw",raw),name,()->PropertyLocationComparison.districtKey(raw));
            }
            Instant now=Instant.parse("2026-10-25T00:00:00Z");
            for(int i=0;i<200;i++){
                int limit=i+1;boolean login=i%2==0;
                check("auth-rate-limit",i,map("limit",limit,"login",login),true,()->{
                    var limiter=new AuthRateLimiter(new AuthRateLimitProperties(limit,limit+1,Duration.ofSeconds(60),10),Clock.fixed(now,ZoneOffset.UTC));
                    int n=login?limit:limit+1;for(int k=0;k<n;k++)if(!limiter.allow("127.0.0.1",login))return false;
                    return !limiter.allow("127.0.0.1",login)&&limiter.allow("127.0.0.2",login);
                });
            }
            for(int i=0;i<180;i++){
                int sample=i;String email="synthetic-"+i+"@example.invalid",mode=List.of("valid","expired","tampered").get(i%3);
                check("jwt-validation",i,map("identity",email,"mode",mode),mode.equals("valid"),()->{
                    var policy=new JwtProperties(Duration.ofHours(1),Duration.ofDays(3),SECRET);
                    var issuer=new JwtService(policy,Clock.fixed(now,ZoneOffset.UTC));String token=issuer.generateAccessToken(email);
                    if(sample%3==2){String[] parts=token.split("\\.");parts[2]=(parts[2].startsWith("A")?"B":"A")+parts[2].substring(1);token=String.join(".",parts);}
                    var reader=new JwtService(policy,Clock.fixed(sample%3==1?now.plusSeconds(3600):now,ZoneOffset.UTC));
                    try{return reader.validateAccessToken(token).getSubject().equals(email);}catch(io.jsonwebtoken.JwtException e){return false;}
                });
            }
            BoundedBCryptPasswordEncoder encoder=new BoundedBCryptPasswordEncoder();
            for(int i=0;i<20;i++){
                boolean suffix=i<10;String prefix=i%2==0?("A".repeat(70)+String.format("%02d",i)):("ع".repeat(35)+(char)('ب'+i));
                // Approved policy: valid 72-byte input works; modified prefix or oversized suffix does not.
                String password=prefix,wrong=suffix?prefix+"-different":"Z"+password.substring(1);
                check("bcrypt-distinct-password",i,map("alphabet",i%2==0?"ASCII":"Arabic","mutation",suffix?"after-72-bytes":"within-72-bytes","prefixSample",i),false,()->{
                    String hash=encoder.encode(password);
                    if(!encoder.matches(password,hash))throw new AssertionError("valid boundary password rejected");
                    return encoder.matches(wrong,hash);
                });
            }
            if(total!=4000)throw new IllegalStateException("Expected 4000 cases, got "+total);
            // Synthetic local image fixture for HTTP tests; never an original/user photograph.
            BufferedImage image=new BufferedImage(800,600,BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<600;y++)for(int x=0;x<800;x++)image.setRGB(x,y,((x/20)%2==0?0x902d3b:0xeee0bf));
            ImageIO.write(image,"jpeg",Path.of(args[1],"synthetic.jpg").toFile());image.flush();
            System.out.println(JSON.writeValueAsString(map("layer","backend","total",total,"passed",total-failed,"failed",failed,"groups",GROUPS)));
        } finally {out.close();FACTORY.close();}
        if(failed>0)System.exit(2);
    }
}
