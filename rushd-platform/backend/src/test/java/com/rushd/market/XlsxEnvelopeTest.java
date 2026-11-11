package com.rushd.market;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class XlsxEnvelopeTest {
    static Map<String,String> parts() { return new LinkedHashMap<>(Map.of("[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/></Types>","xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheets/></workbook>")); }
    static byte[] workbook() { return zip(parts()); }
    static byte[] zip(Map<String,String> parts) {
        try { var out=new ByteArrayOutputStream(); try(var zip=new ZipOutputStream(out)) { for(var p:parts.entrySet()) { var entry=new ZipEntry(p.getKey()); entry.setTime(0); zip.putNextEntry(entry); zip.write(p.getValue().getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); } } return out.toByteArray(); }
        catch(IOException ex) { throw new AssertionError(ex); }
    }
    @Test void acceptsSyntheticEnvelopeAndLeavesBytesUnchanged() { byte[] original=workbook(),copy=original.clone(); XlsxEnvelope.validate(original); assertArrayEquals(copy,original); }
    @Test void rejectsGenericZipHtmlEmptyOversizedAndTruncatedFiles() {
        for(byte[] b:List.of(new byte[0],new byte[10*1024*1024+1],"<html/>".getBytes(),zip(Map.of("text.txt","not xlsx")),Arrays.copyOf(workbook(),workbook().length-1))) assertThrows(MarketFailure.class,()->XlsxEnvelope.validate(b));
    }
    @Test void rejectsTraversalMacrosExternalLinksAndExternalEntities() {
        for(var entry:Map.of("../escape","x","/absolute","x","xl/vbaProject.bin","x","xl/externalLinks/x.xml","x","nested.zip","x","_rels/.rels","<Relationships><Relationship TargetMode=\"External\" Target=\"https://example.invalid\"/></Relationships>","xl/workbook.xml","<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><workbook>&e;</workbook>").entrySet()) {
            var parts=parts();parts.put(entry.getKey(),entry.getValue());
            assertThrows(MarketFailure.class,()->XlsxEnvelope.validate(zip(parts)));
        }
    }
    @Test void rejectsInflationBombAndTooManyEntries() {
        assertThrows(MarketFailure.class,()->XlsxEnvelope.validate(zip(Map.of("xl/worksheets/sheet1.xml","x".repeat(65*1024*1024)))));
        Map<String,String> entries=new LinkedHashMap<>(); for(int i=0;i<2049;i++) entries.put("x"+i,"");
        assertThrows(MarketFailure.class,()->XlsxEnvelope.validate(zip(entries)));
    }
    @Test void rejectsInconsistentCentralDirectoryAndEncryptedFlags() {
        byte[] bytes=workbook();
        for(int i=0;i<bytes.length-46;i++) if(bytes[i]==0x50 && bytes[i+1]==0x4b && bytes[i+2]==1 && bytes[i+3]==2) {
            byte[] renamed=bytes.clone();renamed[i+46]='Z';assertThrows(MarketFailure.class,()->XlsxEnvelope.validate(renamed));
            byte[] encrypted=bytes.clone();encrypted[i+8]|=1;assertThrows(MarketFailure.class,()->XlsxEnvelope.validate(encrypted));return;
        }
        fail("Synthetic workbook did not contain a ZIP central directory");
    }
}
