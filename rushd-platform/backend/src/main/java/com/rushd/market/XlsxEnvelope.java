package com.rushd.market;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

/** Bounded package check, not a malware scanner or worksheet importer. Never extracts files. */
final class XlsxEnvelope {
    static final int MAX_BYTES = 10 * 1024 * 1024;
    static void validate(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new MarketFailure(413,"حجم الأصل يجب ألا يتجاوز 10MiB.");
        // Require a complete single-disk ZIP, with no comment/trailing payload. ZIP64 is outside this small-file contract.
        int e = bytes.length - 22;
        if (e < 0 || u32(bytes,e) != 0x06054b50L || u16(bytes,e+4)!=0 || u16(bytes,e+6)!=0
                || u16(bytes,e+8)!=u16(bytes,e+10) || u16(bytes,e+20)!=0
                || u32(bytes,e+12)+u32(bytes,e+16)!=e) throw invalid();
        Set<String> entries = new HashSet<>();
        boolean types=false, workbook=false;
        long total=0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry=zip.getNextEntry())!=null) {
                String name=entry.getName(), lower=name.toLowerCase(Locale.ROOT);
                if (entries.size()>=2048 || !entries.add(name) || name.startsWith("/") || name.contains("\\") || name.contains(":")
                        || Arrays.asList(name.split("/")).contains("..") || name.codePoints().anyMatch(Character::isISOControl)
                        || lower.endsWith(".bin") || lower.endsWith(".zip") || lower.contains("externallinks/") || lower.contains("vbaproject")) throw invalid();
                boolean descriptor=name.equals("[Content_Types].xml") || name.equals("xl/workbook.xml") || lower.endsWith(".rels");
                ByteArrayOutputStream xml = descriptor ? new ByteArrayOutputStream() : null;
                int n;
                while ((n=zip.read(buffer))!=-1) {
                    total+=n;
                    if (total>64L*1024*1024) throw invalid();
                    if (descriptor) { if (xml.size()+n>1024*1024) throw invalid(); xml.write(buffer,0,n); }
                }
                if (descriptor) {
                    Document doc=xml(xml.toByteArray());
                    var all=doc.getElementsByTagNameNS("*","*");
                    for (int i=0;i<all.getLength();i++) {
                        var el=(org.w3c.dom.Element)all.item(i);
                        if ("External".equalsIgnoreCase(el.getAttribute("TargetMode"))) throw invalid();
                        String content=el.getAttribute("ContentType");
                        if (content.toLowerCase(Locale.ROOT).contains("macro") || content.toLowerCase(Locale.ROOT).contains("activex")) throw invalid();
                        if (name.equals("[Content_Types].xml") && "/xl/workbook.xml".equals(el.getAttribute("PartName"))
                                && "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml".equals(content)) types=true;
                    }
                    if (name.equals("xl/workbook.xml")) workbook="workbook".equals(doc.getDocumentElement().getLocalName())
                            && "http://schemas.openxmlformats.org/spreadsheetml/2006/main".equals(doc.getDocumentElement().getNamespaceURI());
                }
            }
            if (!types || !workbook || entries.size()!=u16(bytes,e+10)) throw invalid();
            centralDirectory(bytes,e,entries);
        } catch (IOException ex) { throw invalid(); }
    }
    private static void centralDirectory(byte[] bytes,int end,Set<String> localNames) {
        long start=u32(bytes,end+16);
        if (start<0 || start>end) throw invalid();
        int pos=(int)start; Set<String> names=new HashSet<>(); Set<Long> offsets=new HashSet<>();
        long inflated=0;
        while(pos<end) {
            if (pos+46>end || u32(bytes,pos)!=0x02014b50L || (u16(bytes,pos+8)&1)!=0 || u16(bytes,pos+34)!=0) throw invalid();
            int method=u16(bytes,pos+10), nameLength=u16(bytes,pos+28);
            long next=(long)pos+46+nameLength+u16(bytes,pos+30)+u16(bytes,pos+32), offset=u32(bytes,pos+42);
            inflated+=u32(bytes,pos+24);
            if ((method!=0 && method!=8) || next>end || offset+30>=start || !offsets.add(offset) || inflated>64L*1024*1024) throw invalid();
            int local=(int)offset;
            if (u32(bytes,local)!=0x04034b50L || u16(bytes,local+26)!=nameLength || (long)local+30+nameLength>start) throw invalid();
            String name=new String(bytes,pos+46,nameLength,StandardCharsets.UTF_8);
            if (!name.equals(new String(bytes,local+30,nameLength,StandardCharsets.UTF_8)) || !names.add(name) || !localNames.contains(name)) throw invalid();
            pos=(int)next;
        }
        if (!names.equals(localNames)) throw invalid();
    }
    private static Document xml(byte[] bytes) {
        try {
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true); factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            var builder=factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            return builder.parse(new ByteArrayInputStream(bytes));
        } catch (Exception ex) { throw invalid(); }
    }
    private static int u16(byte[] b,int n) { return (b[n]&255)|((b[n+1]&255)<<8); }
    private static long u32(byte[] b,int n) { return (long)u16(b,n)|((long)u16(b,n+2)<<16); }
    private static MarketFailure invalid() { return MarketFailure.invalid("الأصل ليس مغلف XLSX مدعومًا، أو يحتوي مكونات محظورة أو يتجاوز حدود الفحص."); }
}
