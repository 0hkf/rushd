package com.rushd.images;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.entity.*;
import com.rushd.repository.*;
import com.rushd.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real Tomcat multipart + cookie/XOR-CSRF path; no MockMvc CSRF postprocessor. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"rushd.images.enabled=true","server.address=127.0.0.1"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ImageHttpIntegrationTest {
    static final Path ROOT=ImageFixtures.privateRoot();
    @DynamicPropertySource static void config(DynamicPropertyRegistry registry) {registry.add("rushd.images.root",ROOT::toString);}
    @LocalServerPort int port;
    @Autowired PropertyRepository properties;
    @Autowired UserRepository users;
    @Autowired PropertyImages images;
    @Autowired ImageCollections collections;
    @Autowired PrivateImageStorage storage;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager manager;
    static PrivateImageStorage ownedStorage;
    Long propertyId,userId;
    String email,csrf,base;
    HttpClient client;
    TransactionTemplate tx;
    @BeforeEach void fixture() throws Exception {
        ownedStorage=storage;tx=new TransactionTemplate(manager);base="http://127.0.0.1:"+port;
        email="p10-http-"+UUID.randomUUID()+"@example.invalid";
        tx.executeWithoutResult(s->{
            var admin=new User();admin.setName("P10 synthetic HTTP");admin.setEmail(email);admin.setPassword("unused");admin.setRole(Role.ADMIN);users.saveAndFlush(admin);userId=admin.getId();
            var property=new Property();property.setPublisher(admin);property.setTitle("P10 synthetic HTTP");property.setType(PropertyType.LAND);
            property.setCity("مكة");property.setDistrict("النوارية");property.setArea(new BigDecimal("500.25"));property.setPrice(new BigDecimal("9007199254740993.01"));
            property.setStatus(PropertyStatus.ACTIVE);properties.saveAndFlush(property);propertyId=property.getId();
        });
        var cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        var access=new HttpCookie(AuthCookieService.ACCESS,jwt.generateAccessToken(email));access.setPath("/");access.setVersion(0);
        cookies.getCookieStore().add(URI.create(base),access);
        client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).cookieHandler(cookies).build();
        var proof=send(client,HttpRequest.newBuilder(URI.create(base+"/api/auth/csrf")).GET());assertEquals(200,proof.statusCode());
        csrf=mapper.readTree(proof.body()).get("token").asText();
    }
    @AfterEach void cleanup() {
        tx.executeWithoutResult(s->{
            collections.findById(propertyId).ifPresent(c->{c.primaryImageId=null;collections.saveAndFlush(c);});
            var rows=images.findByPropertyId(propertyId);rows.forEach(i->storage.remove(i.storageKey));images.deleteAll(rows);images.flush();
            collections.deleteById(propertyId);collections.flush();properties.deleteById(propertyId);properties.flush();users.deleteById(userId);users.flush();
        });
    }
    @AfterAll static void removeRoot() throws Exception {
        if(ownedStorage!=null)ownedStorage.close();
        try(var entries=Files.list(ROOT)){for(Path file:entries.toList())Files.delete(file);}Files.delete(ROOT);
    }
    HttpResponse<byte[]> send(HttpClient http,HttpRequest.Builder request) throws Exception {
        return http.send(request.timeout(Duration.ofSeconds(15)).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    String path(){return base+"/api/admin/properties/"+propertyId+"/images";}
    HttpRequest.Builder multipart(byte[] source) throws Exception {
        String boundary="P10"+UUID.randomUUID();var bytes=new ByteArrayOutputStream();
        for(var field:Map.of("uploadToken",UUID.randomUUID().toString(),"rightsConfirmed","true","rightsBasis","OWNED").entrySet())
            bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+field.getKey()+"\"\r\n\r\n"+field.getValue()+"\r\n").getBytes(StandardCharsets.US_ASCII));
        bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"synthetic.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        bytes.write(source);bytes.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.US_ASCII));
        return HttpRequest.newBuilder(URI.create(path())).header("Content-Type","multipart/form-data; boundary="+boundary)
                .header("X-XSRF-TOKEN",csrf).POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray()));
    }
    @Test void realMultipartUploadEditReadDeleteWithCookiesAndCsrf() throws Exception {
        var result=send(client,multipart(ImageFixtures.image("jpeg",640,480,false)));
        assertEquals(201,result.statusCode(),new String(result.body(),StandardCharsets.UTF_8));
        var manifest=mapper.readValue(result.body(),ImageManifest.class);UUID id=manifest.primaryImageId();
        var guest=HttpClient.newHttpClient();String content=base+"/api/properties/"+propertyId+"/images/"+id+"/display";
        var image=send(guest,HttpRequest.newBuilder(URI.create(content)).GET());assertEquals(200,image.statusCode());
        assertEquals("no-store",image.headers().firstValue("Cache-Control").orElseThrow());assertNotNull(javax.imageio.ImageIO.read(new ByteArrayInputStream(image.body())));
        var update=new ImageManifestRequest("1",id,List.of(new ImageManifestRequest.Item(id,"صورة صناعية للاختبار")));
        var edited=send(client,HttpRequest.newBuilder(URI.create(path())).header("X-XSRF-TOKEN",csrf).header("Content-Type","application/json")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(update))));assertEquals(200,edited.statusCode());
        var deleted=send(client,HttpRequest.newBuilder(URI.create(path()+"/"+id+"?expectedRevision=2")).header("X-XSRF-TOKEN",csrf).DELETE());assertEquals(204,deleted.statusCode());
        assertEquals(404,send(guest,HttpRequest.newBuilder(URI.create(content)).GET()).statusCode());
    }
    @Test void reservationAndCsrfRejectBeforeMalformedMultipartIsParsed() throws Exception {
        var request=HttpRequest.newBuilder(URI.create(path())).header("Content-Type","multipart/form-data; boundary=invalid")
                .POST(HttpRequest.BodyPublishers.ofString("not a multipart body"));
        assertEquals(403,send(client,request).statusCode());
        try(var lease=storage.reserveUpload()) {
            request.header("X-XSRF-TOKEN",csrf);
            var busy=send(client,request);assertEquals(429,busy.statusCode());assertEquals("2",busy.headers().firstValue("Retry-After").orElseThrow());
        }
        assertTrue(images.findByPropertyId(propertyId).isEmpty());
    }
    @Test void realServletRejectsSourceOverEightMiB() throws Exception {
        var result=send(client,multipart(new byte[ImageProcessor.MAX_SOURCE+1]));assertEquals(413,result.statusCode());
        assertTrue(images.findByPropertyId(propertyId).isEmpty());
    }
}
