package com.rushd.images;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

/** Single-process local store. No user-controlled paths and no static resource mapping. */
@Component
public class PrivateImageStorage {
    static final long MIB = 1024L * 1024;
    static final long CAPACITY = 512 * MIB;
    static final long MIN_FREE = 2048 * MIB;
    static final long RESERVATION = 12 * MIB;
    private final boolean enabled;
    private final String configuredRoot;
    private final ReentrantLock lock = new ReentrantLock();
    private FileChannel ownerChannel;
    private FileLock ownerLock;
    private Object rootIdentity;
    public PrivateImageStorage(@Value("${rushd.images.enabled:false}") boolean enabled,
                               @Value("${rushd.images.root:}") String root) {
        this.enabled = enabled; this.configuredRoot = root;
    }
    public boolean enabled() { return enabled; }
    public void requireEnabled() { if (!enabled) throw ImageFailure.unavailable(); }
    public void requireAvailable() { root(); }
    public void requireUploadReservation() {
        if (!lock.isHeldByCurrentThread()) throw ImageFailure.unavailable();
    }

    /** Held by the security filter BEFORE DispatcherServlet parses multipart. Never queues uploads. */
    public AutoCloseable reserveUpload() {
        requireEnabled();
        if (!lock.tryLock()) throw new ImageFailure(429, "Another image operation is in progress; retry later");
        try {
            Path root = root();
            if (usedBytes(root) > CAPACITY - RESERVATION || usableBytes(root) < MIN_FREE + RESERVATION)
                throw ImageFailure.unavailable();
            return lock::unlock;
        } catch (RuntimeException e) { lock.unlock(); throw e; }
    }
    protected long usableBytes(Path root) {
        try { return Files.getFileStore(root).getUsableSpace(); }
        catch (IOException e) { throw ImageFailure.unavailable(); }
    }
    synchronized Path root() {
        requireEnabled();
        try {
            if (configuredRoot.isBlank()) throw ImageFailure.unavailable();
            Path root = Path.of(configuredRoot);
            if (!root.isAbsolute() || !root.equals(root.normalize())) throw ImageFailure.unavailable();
            Path part = root.getRoot();
            for (Path segment : root) {
                part = part.resolve(segment);
                if (Files.isSymbolicLink(part)) throw ImageFailure.unavailable();
            }
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw ImageFailure.unavailable();
            Object identity = Files.readAttributes(root,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey();
            if (identity == null || rootIdentity != null && !rootIdentity.equals(identity)) throw ImageFailure.unavailable();
            // Refuse any Git checkout, not merely frontend/public. Operator creates the root, not this app.
            for (Path ancestor = root; ancestor != null; ancestor = ancestor.getParent()) {
                if (Files.exists(ancestor.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) throw ImageFailure.unavailable();
            }
            var permissions = Files.getPosixFilePermissions(root, LinkOption.NOFOLLOW_LINKS);
            var expectedOwner = root.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name"));
            if (!Files.getOwner(root, LinkOption.NOFOLLOW_LINKS).equals(expectedOwner)
                    || !permissions.containsAll(PosixFilePermissions.fromString("rwx------"))
                    || permissions.stream().anyMatch(p -> p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_")))
                throw ImageFailure.unavailable();
            if (ownerLock == null) {
                Path ownerFile = root.resolve(".rushd-store.lock");
                if (Files.isSymbolicLink(ownerFile)) throw ImageFailure.unavailable();
                ownerChannel = FileChannel.open(ownerFile, Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                try { ownerLock = ownerChannel.tryLock(); }
                catch (OverlappingFileLockException e) { ownerChannel.close(); ownerChannel = null; throw ImageFailure.unavailable(); }
                if (ownerLock == null) { ownerChannel.close(); ownerChannel = null; throw ImageFailure.unavailable(); }
                rootIdentity = identity;
            }
            return root;
        } catch (IOException | InvalidPathException | UnsupportedOperationException e) { throw ImageFailure.unavailable(); }
    }
    @jakarta.annotation.PreDestroy
    public synchronized void close() {
        try { if (ownerLock != null) ownerLock.release(); }
        catch (IOException ignored) { /* shutdown only */ }
        try { if (ownerChannel != null) ownerChannel.close(); }
        catch (IOException ignored) { /* shutdown only */ }
        ownerLock = null; ownerChannel = null; rootIdentity = null;
    }
    private long usedBytes(Path root) {
        try (var entries = Files.list(root)) {
            long total = 0;
            for (Path file : entries.toList()) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw ImageFailure.unavailable();
                total = Math.addExact(total, Files.size(file));
            }
            return total;
        } catch (IOException | ArithmeticException e) { throw ImageFailure.unavailable(); }
    }
    private Path path(UUID key, String variant) {
        if (!Set.of("display", "thumbnail").contains(variant)) throw ImageFailure.missing();
        return root().resolve(key + "-" + variant + ".jpg");
    }
    public void write(UUID key, ImageProcessor.Processed processed) {
        if (!lock.isHeldByCurrentThread()) throw ImageFailure.unavailable();
        List<Path> staged = new ArrayList<>();
        List<Path> published = new ArrayList<>();
        try {
            for (String variant : List.of("display", "thumbnail")) {
                Path target = path(key, variant);
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw ImageFailure.unavailable();
                Path stage = Files.createTempFile(root(), "stage-", ".part",
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                staged.add(stage);
                Files.write(stage, variant.equals("display") ? processed.display() : processed.thumbnail(), StandardOpenOption.WRITE);
                Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE);
                published.add(target);
            }
        } catch (IOException | RuntimeException e) {
            // Never remove a pre-existing target. Only paths successfully published by this call are owned.
            for (Path file : published) { try { Files.deleteIfExists(file); } catch (IOException ignored) { /* private orphan; maintenance */ } }
            if (e instanceof ImageFailure failure) throw failure;
            throw ImageFailure.unavailable();
        }
        finally {
            for (Path stage : staged) { try { Files.deleteIfExists(stage); } catch (IOException ignored) { /* maintenance sees leftovers */ } }
        }
    }
    public byte[] read(UUID key, String variant, long expectedSize) {
        lock.lock();
        try {
            Path file = path(key, variant);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) != expectedSize
                    || expectedSize < 1 || expectedSize > (variant.equals("display") ? 2 * MIB : 256 * 1024)) throw ImageFailure.unavailable();
            // NOFOLLOW_LINKS applies to the actual open, not just an earlier existence check.
            try (var in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = in.readNBytes((int) expectedSize + 1);
                if (bytes.length != expectedSize) throw ImageFailure.unavailable();
                return bytes;
            }
        } catch (IOException e) { throw ImageFailure.unavailable(); }
        finally { lock.unlock(); }
    }
    /** Revocation must be committed in DB before calling this method. */
    public boolean remove(UUID key) {
        lock.lock();
        try {
            for (String variant : List.of("display", "thumbnail")) {
                Path file = path(key, variant);
                if (Files.isSymbolicLink(file)) return false;
                Files.deleteIfExists(file);
            }
            return true;
        } catch (IOException | ImageFailure e) { return false; }
        finally { lock.unlock(); }
    }
    public boolean absent(UUID key) {
        lock.lock();
        try { return !Files.exists(path(key,"display"),LinkOption.NOFOLLOW_LINKS)
                && !Files.exists(path(key,"thumbnail"),LinkOption.NOFOLLOW_LINKS); }
        finally { lock.unlock(); }
    }
    public <T> T exclusive(java.util.function.Supplier<T> operation) {
        lock.lock();
        try { requireAvailable(); return operation.get(); }
        finally { lock.unlock(); }
    }
    public record MaintenanceEntry(String name, long bytes, boolean removed) {}
    /** Default callers use apply=false. Reference supplier is re-read under the same lock as uploads. */
    public List<MaintenanceEntry> maintenance(boolean apply, java.util.function.Supplier<Set<UUID>> references, Instant now) {
        lock.lock();
        try {
            Set<UUID> live = references.get();
            List<MaintenanceEntry> result = new ArrayList<>();
            try (var entries = Files.list(root())) {
                for (Path file : entries.toList()) {
                    String name = file.getFileName().toString();
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                            || !Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(now.minusSeconds(86400))) continue;
                    boolean candidate = name.matches("stage-[A-Za-z0-9_-]+\\.part");
                    if (name.matches("[0-9a-f-]{36}-(display|thumbnail)\\.jpg")) {
                        UUID key;
                        try { key = UUID.fromString(name.substring(0, 36)); }
                        catch (IllegalArgumentException ignored) { continue; }
                        candidate = !live.contains(key);
                    }
                    if (!candidate) continue;
                    long bytes = Files.size(file);
                    // The supplier is called again immediately before unlink while the store lock is held.
                    if (apply && !references.get().equals(live)) throw ImageFailure.unavailable();
                    if (apply) Files.delete(file);
                    result.add(new MaintenanceEntry(name, bytes, apply));
                }
            }
            return List.copyOf(result);
        } catch (IOException e) { throw ImageFailure.unavailable(); }
        finally { lock.unlock(); }
    }
}
