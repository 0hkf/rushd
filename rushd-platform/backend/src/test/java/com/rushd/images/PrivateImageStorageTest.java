package com.rushd.images;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PrivateImageStorageTest {
    @TempDir Path temporary;
    PrivateImageStorage storage;
    Path root;
    @BeforeEach void setup() throws Exception {
        root=Files.createDirectory(temporary.resolve("private"),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))).toRealPath();
        storage=new PrivateImageStorage(true,root.toString());
    }
    @AfterEach void close() { storage.close(); }
    @Test void disabledHasNoFilesystemSideEffects() {
        var disabled=new PrivateImageStorage(false,temporary.resolve("does-not-exist").toString());
        assertFalse(disabled.enabled()); assertEquals(503,assertThrows(ImageFailure.class,disabled::reserveUpload).status());
        assertFalse(Files.exists(temporary.resolve("does-not-exist")));
    }
    @Test void rejectsRelativeMissingAndInsecureRoot() throws Exception {
        assertThrows(ImageFailure.class,()->new PrivateImageStorage(true,"relative").reserveUpload());
        assertThrows(ImageFailure.class,()->new PrivateImageStorage(true,root.resolve("missing").toString()).reserveUpload());
        Files.setPosixFilePermissions(root,PosixFilePermissions.fromString("rwxr-xr-x"));
        assertEquals(503,assertThrows(ImageFailure.class,storage::reserveUpload).status());
    }
    @Test void rejectsRootSymlinkAndSecondProcessOwner() throws Exception {
        Path link=temporary.resolve("link"); Files.createSymbolicLink(link,root);
        assertThrows(ImageFailure.class,()->new PrivateImageStorage(true,link.toString()).reserveUpload());
        try(var lease=storage.reserveUpload()) {
            var second=new PrivateImageStorage(true,root.toString());
            assertEquals(503,assertThrows(ImageFailure.class,second::reserveUpload).status()); second.close();
        }
    }
    @Test void concurrentUploadCannotQueueOrReserveTwice() throws Exception {
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try(var lease=storage.reserveUpload()) {
            assertEquals(429,worker.submit(()->assertThrows(ImageFailure.class,storage::reserveUpload).status()).get(5,TimeUnit.SECONDS));
        } finally { worker.shutdownNow(); }
        assertDoesNotThrow(()->{try(var lease=storage.reserveUpload()) {assertNotNull(lease);}});
    }
    @Test void diskFloorAndCapacityIncludeOrphansAndStaging() throws Exception {
        var fullDisk=new PrivateImageStorage(true,root.toString()) { @Override protected long usableBytes(Path ignored) { return MIN_FREE+RESERVATION-1; }};
        assertEquals(503,assertThrows(ImageFailure.class,fullDisk::reserveUpload).status()); fullDisk.close();
        Path orphan=root.resolve("unrecognized-file");
        try(var channel=Files.newByteChannel(orphan,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
            channel.position(PrivateImageStorage.CAPACITY-PrivateImageStorage.RESERVATION); channel.write(java.nio.ByteBuffer.wrap(new byte[]{0}));
        }
        assertEquals(503,assertThrows(ImageFailure.class,storage::reserveUpload).status());
    }
    @Test void maintenanceDryRunAndApplyNeverDeleteReferencedOrRecentOrSymlinkFiles() throws Exception {
        UUID live=UUID.randomUUID(), orphan=UUID.randomUUID(); Instant now=Instant.now();
        Path referenced=root.resolve(live+"-display.jpg"), old=root.resolve(orphan+"-thumbnail.jpg"), recent=root.resolve("stage-recent.part"), stage=root.resolve("stage-old.part");
        for(Path file:List.of(referenced,old,recent,stage)) Files.write(file,new byte[]{1,2,3});
        for(Path file:List.of(referenced,old,stage)) Files.setLastModifiedTime(file,FileTime.from(now.minusSeconds(86401)));
        Path canary=temporary.resolve("canary");Files.writeString(canary,"preserve");Path symlink=root.resolve(UUID.randomUUID()+"-display.jpg"); Files.createSymbolicLink(symlink,canary);
        assertEquals(2,storage.maintenance(false,()->Set.of(live),now).size()); assertTrue(Files.exists(old));assertTrue(Files.exists(stage));
        assertEquals(2,storage.maintenance(true,()->Set.of(live),now).size());assertFalse(Files.exists(old));assertFalse(Files.exists(stage));
        assertTrue(Files.exists(referenced));assertTrue(Files.exists(recent));assertEquals("preserve",Files.readString(canary));assertTrue(Files.isSymbolicLink(symlink));
    }
    @Test void readsAndRemovalsNeverFollowSymlinks() throws Exception {
        UUID key=UUID.randomUUID();Path canary=temporary.resolve("canary");Files.writeString(canary,"secret");
        Path file=root.resolve(key+"-display.jpg");Files.createSymbolicLink(file,canary);
        assertThrows(ImageFailure.class,()->storage.read(key,"display",6));assertFalse(storage.remove(key));assertEquals("secret",Files.readString(canary));
    }
    @Test void csrfForUploadNeverReadsMultipartParametersBeforeGuard() {
        var request=new org.springframework.mock.web.MockHttpServletRequest("POST","/api/admin/properties/1/images") {
            @Override public String getParameter(String name) { throw new AssertionError("multipart parsed before guard"); }
        };
        var token=new org.springframework.security.web.csrf.DefaultCsrfToken("X-XSRF-TOKEN","_csrf","secret");
        assertNull(new ImageCsrfTokenRequestHandler().resolveCsrfTokenValue(request,token));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"-1","invalid","9223372036854775808"})
    void malformedPropertyIdsCannotBypassPreMultipartReservation(String id) {
        var request=new org.springframework.mock.web.MockHttpServletRequest("POST","/api/admin/properties/"+id+"/images") {
            @Override public String getParameter(String name){throw new AssertionError("parsed multipart for invalid id");}
        };
        var token=new org.springframework.security.web.csrf.DefaultCsrfToken("X-XSRF-TOKEN","_csrf","secret");
        assertNull(new ImageCsrfTokenRequestHandler().resolveCsrfTokenValue(request,token));
        assertFalse(new ImageUploadGuard(storage,new com.rushd.security.SecurityErrorWriter(new com.fasterxml.jackson.databind.ObjectMapper())).shouldNotFilter(request));
    }
    @Test void exactCapacityAndDiskFloorReservationBoundaryIsAccepted() throws Exception {
        storage.close();
        storage=new PrivateImageStorage(true,root.toString()){@Override protected long usableBytes(Path ignored){return MIN_FREE+RESERVATION;}};
        Path file=root.resolve("stage-capacity.part");
        try(var channel=Files.newByteChannel(file,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
            channel.position(PrivateImageStorage.CAPACITY-PrivateImageStorage.RESERVATION-1);channel.write(java.nio.ByteBuffer.wrap(new byte[]{0}));
        }
        try(var lease=storage.reserveUpload()){assertNotNull(lease);}
    }
    @Test void partialMoveFailureNeverDeletesAPreExistingTarget() throws Exception {
        UUID key=UUID.randomUUID();Path existing=root.resolve(key+"-thumbnail.jpg");Files.writeString(existing,"preserve existing file");
        var image=new ImageProcessor().process(ImageFixtures.image("jpeg",640,480,false),"a.jpg","image/jpeg");
        try(var lease=storage.reserveUpload()) {assertThrows(ImageFailure.class,()->storage.write(key,image));}
        assertEquals("preserve existing file",Files.readString(existing));assertFalse(Files.exists(root.resolve(key+"-display.jpg")));
        try(var files=Files.list(root)) {assertTrue(files.noneMatch(p->p.getFileName().toString().endsWith(".part")));}
    }
}
