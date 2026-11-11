package com.rushd.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.rushd.entity.Role;
import com.rushd.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.security.*;
import java.time.Clock;
import java.util.*;

@Service
public class MarketWorkflow {
    private static final long QUOTA = 100L*1024*1024;
    private final MarketStore store;
    private final UserRepository users;
    private final Clock clock;
    private final boolean enabled;
    public record SourceView(String id, MarketInput.Source metadata, boolean ready, String sha256, Long size,
                             boolean archived, String stateReason, String revision, String createdBy, String changedBy,
                             java.time.Instant createdAt, java.time.Instant updatedAt) { }
    public record ReferenceView(String id, String sourceId, String sourceReference, String revision, MarketInput.Facts facts,
                                boolean archived, String stateReason, String changeReason, String changedBy,
                                java.time.Instant changedAt, List<String> missingData) { }
    public MarketWorkflow(JdbcTemplate jdbc, UserRepository users, Clock clock, @Value("${rushd.market.enabled:false}") boolean enabled) {
        this.store=new MarketStore(jdbc); this.users=users; this.clock=clock; this.enabled=enabled;
    }
    @Transactional(readOnly=true)
    public void authorize(String email) { actor(email,false); }
    private long actor(String email, boolean lock) {
        if (!enabled) throw new MarketFailure(503,"إدارة مراجع السوق غير مفعلة في هذه البيئة.");
        var user=(lock?users.findByEmailForUpdate(email):users.findByEmail(email)).orElseThrow(()->new AccessDeniedException("ADMIN required"));
        if (user.getRole()!=Role.ADMIN) throw new AccessDeniedException("ADMIN required");
        return user.getId();
    }
    @Transactional(readOnly=true)
    public List<SourceView> sources(String email,int limit,int offset) {
        actor(email,false); page(limit,offset); return store.sources(limit,offset).stream().map(MarketWorkflow::view).toList();
    }
    @Transactional(readOnly=true)
    public SourceView source(String email,UUID id) { actor(email,false); return view(store.source(id,false)); }
    @Transactional
    public SourceView createSource(String email,JsonNode body) {
        long actor=actor(email,true);
        UUID id=MarketInput.uuid(MarketInput.text(body,"id",36,true));
        var metadata=MarketInput.source(body,clock); String hash=hash(MarketInput.json(metadata).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        store.lockQuota(); // Serializes idempotent creation across accounts; never locks a user's unrelated property.
        var existing=store.existingSource(id);
        if (existing.isPresent()) {
            if (!existing.get().creationHash().equals(hash)) throw MarketFailure.conflict();
            return view(existing.get());
        }
        store.insertSource(id,metadata,hash,actor,clock.instant()); return view(store.source(id,false));
    }
    @Transactional
    public SourceView upload(String email,UUID id,InputStream input) {
        long actor=actor(email,true); // Before even reading an unauthenticated or disabled request body.
        byte[] bytes;
        try { bytes=input.readNBytes(XlsxEnvelope.MAX_BYTES+1); } catch (IOException ex) { throw MarketFailure.invalid("تعذرت قراءة الأصل."); }
        XlsxEnvelope.validate(bytes); String hash=hash(bytes);
        long used=store.lockQuota(); var source=store.source(id,true);
        if (source.sha256()!=null) {
            if (!source.sha256().equals(hash)) throw MarketFailure.conflict();
            return view(source); // Identical lost-reply retry does not consume quota twice.
        }
        if (source.revision()==Long.MAX_VALUE) throw MarketFailure.conflict();
        if (source.archived()) throw MarketFailure.conflict();
        if (used+bytes.length>QUOTA) throw new MarketFailure(409,"حصة الأصول 100MiB ممتلئة؛ لن تحذف الأدلة القديمة تلقائيًا.");
        store.original(id,bytes,hash,actor,clock.instant()); store.useQuota(bytes.length); return view(store.source(id,false));
    }
    @Transactional(readOnly=true)
    public byte[] download(String email,UUID id) {
        actor(email,false); if (store.source(id,false).sha256()==null) throw MarketFailure.missing(); return store.original(id);
    }
    @Transactional
    public SourceView sourceState(String email,UUID id,JsonNode body) {
        long actor=actor(email,true); var source=store.source(id,true);
        check(source.revision(),MarketInput.revision(body));
        String reason=MarketInput.text(body,"reason",1000,true); boolean archived=MarketInput.archived(body);
        store.sourceState(id,archived,reason,actor,clock.instant()); return view(store.source(id,false));
    }
    @Transactional(readOnly=true)
    public List<ReferenceView> references(String email,int limit,int offset) {
        actor(email,false); page(limit,offset); return store.references(limit,offset).stream().map(this::view).toList();
    }
    @Transactional(readOnly=true)
    public ReferenceView reference(String email,UUID id) { actor(email,false); return view(reference(id,false)); }
    @Transactional(readOnly=true)
    public List<ReferenceView> versions(String email,UUID id,int limit,int offset) {
        actor(email,false); page(limit,offset); reference(id,false); return store.versions(id,limit,offset).stream().map(this::view).toList();
    }
    @Transactional
    public ReferenceView createReference(String email,JsonNode body) {
        long actor=actor(email,true); UUID id=MarketInput.uuid(MarketInput.text(body,"id",36,true));
        UUID sourceId=MarketInput.uuid(MarketInput.text(body,"sourceId",36,true));
        var source=store.source(sourceId,true);
        String sourceRef=MarketInput.text(body,"sourceReference",150,true);
        var facts=MarketInput.facts(body,clock,source.metadata());
        String hash=hash((sourceId+"\n"+sourceRef+"\n"+MarketInput.json(facts)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var existing=store.existingReference(id,true);
        if (existing.isPresent()) {
            if (!existing.get().creationHash().equals(hash)) throw MarketFailure.conflict();
            return view(existing.get());
        }
        requireReady(source);
        store.insertReference(id,sourceId,sourceRef,hash);
        var r=new MarketStore.Reference(id,sourceId,sourceRef,hash,0,facts,false,null,"إدخال يدوي",actor,clock.instant());
        store.version(r,facts,false,null,r.changeReason(),actor,clock.instant(),true);
        return view(reference(id,false));
    }
    @Transactional
    public ReferenceView updateReference(String email,UUID id,JsonNode body) {
        long actor=actor(email,true);
        // Same parent-before-head lock order as creation: archiving a source cannot race the readiness check.
        var source=store.source(reference(id,false).sourceId(),true);
        var r=reference(id,true); check(r.revision(),MarketInput.revision(body));
        if (r.archived()) throw MarketFailure.conflict();
        requireReady(source);
        var facts=MarketInput.facts(body,clock,source.metadata());
        String reason=MarketInput.text(body,"changeReason",1000,true);
        store.version(r,facts,false,r.stateReason(),reason,actor,clock.instant(),false); return view(reference(id,false));
    }
    @Transactional
    public ReferenceView referenceState(String email,UUID id,JsonNode body) {
        long actor=actor(email,true); var source=store.source(reference(id,false).sourceId(),true);
        var r=reference(id,true); check(r.revision(),MarketInput.revision(body));
        boolean archived=MarketInput.archived(body); String reason=MarketInput.text(body,"reason",1000,true);
        if (!archived) requireReady(source);
        store.version(r,r.facts(),archived,reason,reason,actor,clock.instant(),false); return view(reference(id,false));
    }
    private MarketStore.Reference reference(UUID id,boolean lock) { return store.existingReference(id,lock).orElseThrow(MarketFailure::missing); }
    private static void check(long actual,long expected) { if (actual!=expected || actual==Long.MAX_VALUE) throw MarketFailure.conflict(); }
    private static void requireReady(MarketStore.Source s) { if (s.archived() || s.sha256()==null) throw new MarketFailure(409,"يلزم مصدر غير مؤرشف وأصل محفوظ أولًا."); }
    private static void page(int limit,int offset) { if (limit<1 || limit>50 || offset<0 || offset>1000000) throw MarketFailure.invalid("حدود الصفحة غير صالحة."); }
    private static SourceView view(MarketStore.Source s) { return new SourceView(s.id().toString(),s.metadata(),s.sha256()!=null,s.sha256(),s.size(),s.archived(),s.stateReason(),Long.toString(s.revision()),Long.toString(s.createdBy()),Long.toString(s.changedBy()),s.createdAt(),s.updatedAt()); }
    private ReferenceView view(MarketStore.Reference r) {
        var f=r.facts(); List<String> flags=new ArrayList<>();
        if (f.propertyType()==null) flags.add("MISSING_DETAILED_TYPE");
        if (f.areaMeaning()==null) flags.add("MISSING_AREA_MEANING");
        if (f.area()==null) flags.add("MISSING_AREA");
        if (f.district()==null) flags.add("MISSING_DISTRICT");
        if (f.propertyCount()==null) flags.add("UNKNOWN_PROPERTY_COUNT");
        else if (f.propertyCount()>1) flags.add("MULTIPLE_PROPERTIES");
        if (store.source(r.sourceId(),false).archived()) flags.add("SOURCE_ARCHIVED");
        if (r.archived()) flags.add("REFERENCE_ARCHIVED");
        return new ReferenceView(r.id().toString(),r.sourceId().toString(),r.sourceReference(),Long.toString(r.revision()),f,r.archived(),r.stateReason(),r.changeReason(),Long.toString(r.changedBy()),r.changedAt(),List.copyOf(flags));
    }
    static String hash(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); } }
}
