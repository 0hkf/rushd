package com.rushd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.dto.UpdatePropertyRequest;
import com.rushd.entity.*;
import com.rushd.repository.PropertyRepository;
import com.rushd.repository.UserRepository;
import com.rushd.service.PropertyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static com.rushd.controller.PropertySpecificationsFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PropertySpecificationsConcurrencyTest {
    @Autowired PlatformTransactionManager manager;
    @Autowired PropertyRepository properties;
    @Autowired UserRepository users;
    @Autowired PropertyService service;
    @Autowired ObjectMapper mapper;

    @Test void simultaneousPartialSpecificationEditsRetainBothChanges() throws Exception {
        TransactionTemplate tx=new TransactionTemplate(manager);
        String email="p04-concurrency@example.invalid";
        long[] ids=tx.execute(status->{
            User admin=new User(); admin.setName("P04 concurrency"); admin.setEmail(email); admin.setPassword("unused"); admin.setRole(Role.ADMIN);
            users.saveAndFlush(admin);
            Property property=new Property(); property.setPublisher(admin); property.setTitle("P04 property"); property.setType(PropertyType.VILLA);
            property.setCity("مكة المكرمة"); property.setDistrict("النوارية"); property.setPurpose(PropertyPurpose.RESIDENTIAL);
            property.setArea(new BigDecimal("500.25")); property.setPrice(new BigDecimal("9007199254740993.01"));
            properties.saveAndFlush(property);
            return new long[]{property.getId(),admin.getId()};
        });
        UpdatePropertyRequest first=mapper.readValue(mapper.writeValueAsBytes(body(PropertyType.VILLA,values("hasPool",true))),UpdatePropertyRequest.class);
        UpdatePropertyRequest second=mapper.readValue(mapper.writeValueAsBytes(body(PropertyType.VILLA,values("hasYard",true))),UpdatePropertyRequest.class);
        CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1),started=new CountDownLatch(1),secondLoaded=new CountDownLatch(1);
        ExecutorService workers=Executors.newFixedThreadPool(2);
        Future<?> a=null,b=null;
        try {
            a=workers.submit(()->tx.execute(status->{
                properties.findByIdForUpdate(ids[0]).orElseThrow(); locked.countDown();
                await(release); service.updateProperty(ids[0],first,email); return null;
            }));
            assertTrue(locked.await(10,TimeUnit.SECONDS));
            b=workers.submit(()->tx.execute(status->{
                started.countDown(); properties.findByIdForUpdate(ids[0]).orElseThrow(); secondLoaded.countDown();
                service.updateProperty(ids[0],second,email); return null;
            }));
            assertTrue(started.await(10,TimeUnit.SECONDS));
            assertFalse(secondLoaded.await(150,TimeUnit.MILLISECONDS),"A second writer must wait for the first row transaction");
            release.countDown(); a.get(15,TimeUnit.SECONDS); b.get(15,TimeUnit.SECONDS);
            tx.executeWithoutResult(status->{
                var stored=properties.findById(ids[0]).orElseThrow().getSpecifications();
                assertTrue(stored.getHasPool()); assertTrue(stored.getHasYard()); assertNull(stored.getParkingSpaces());
            });
        } finally {
            release.countDown();
            workers.shutdown();
            if(!workers.awaitTermination(20,TimeUnit.SECONDS)) workers.shutdownNow();
            tx.executeWithoutResult(status->{properties.deleteById(ids[0]);properties.flush();users.deleteById(ids[1]);});
        }
    }
    private static void await(CountDownLatch latch) {
        try { if(!latch.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Test latch timed out"); }
        catch(InterruptedException ex) {Thread.currentThread().interrupt();throw new IllegalStateException(ex);}
    }
}
