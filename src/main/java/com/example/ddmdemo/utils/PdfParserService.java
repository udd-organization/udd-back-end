package com.example.ddmdemo.utils;

import com.example.ddmdemo.dto.IncidentReportDto;
import com.example.ddmdemo.enums.SeverityLevel;
import lombok.NoArgsConstructor;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NoArgsConstructor
@Service
public class PdfParserService {
    public IncidentReportDto parse(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            BodyContentHandler handler = new BodyContentHandler(-1); // unlimited
            Metadata metadata = new Metadata();
            ParseContext ctx = new ParseContext();
            PDFParser parser = new PDFParser();

            parser.parse(is, handler, metadata, ctx);

            String raw = handler.toString();
            String text = normalizeForIndexing(raw);

            String employeeFullName        = find(text, "(?i)employee\\s*name\\s*[:\\-]\\s*(.+)");
            String securityOrganization    = find(text, "(?i)security\\s*organization\\s*[:\\-]\\s*(.+)");
            String attackedOrganization    = find(text, "(?i)(attacked|victim|target)\\s*organization\\s*[:\\-]\\s*(.+)");
            String attackedOrgAddress      = find(text, "(?i)(address|location)\\s*[:\\-]\\s*(.+)");
            SeverityLevel severity         = parseSeverity(find(text, "(?i)severity\\s*[:\\-]\\s*(LOW|MEDIUM|HIGH|CRITICAL)"));

            return new IncidentReportDto(
                    nz(employeeFullName),
                    nz(securityOrganization),
                    nz(attackedOrganization),
                    severity != null ? severity : SeverityLevel.LOW,
                    nz(attackedOrgAddress),
                    text
            );

        } catch (IOException | SAXException | TikaException e) {
            throw new RuntimeException("Failed to parse PDF", e);
        }
    }

    private static String normalizeForIndexing(String s) {
        if (s == null) return "";
        // dehyphenate lines split at line end: "bezbedno-\sti" -> "bezbednosti"
        String t = s.replaceAll("(\\p{L})-\\R(\\p{L})", "$1$2");
        // collapse multiple whitespace/newlines
        t = t.replace("\u00A0", " "); // non-breaking space
        t = t.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        t = t.replaceAll("\\R{2,}", "\n"); // keep paragraph breaks
        return t.trim();
    }

    private String find(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (!m.find()) return null;
        // Use the last capturing group to get the value part
        String g = m.group(m.groupCount());
        return g == null ? null : g.trim();
    }

    private SeverityLevel parseSeverity(String s) {
        if (s == null) return null;
        return SeverityLevel.valueOf(s.trim().toUpperCase());
    }

    private String nz(String s) {
        return (s == null) ? "" : s.trim();
    }

    public ParsedPDF importPDF(String filePath) {
        try (FileInputStream inputStream = new FileInputStream(new File(filePath))) {
            BodyContentHandler handler = new BodyContentHandler(-1); // unlimited text
            Metadata metadata = new Metadata();
            ParseContext parseContext = new ParseContext();
            PDFParser pdfParser = new PDFParser();

            pdfParser.parse(inputStream, handler, metadata, parseContext);

            return new ParsedPDF(handler.toString(), toMap(metadata));
        } catch (IOException | SAXException | TikaException e) {
            throw new RuntimeException("Failed to parse PDF", e);
        }
    }

    private Map<String, String> toMap(Metadata metadata) {
        Map<String, String> metamap = new HashMap<>();
        for (String name : metadata.names()) {
            metamap.put(name, metadata.get(name));
        }
        return metamap;
    }

    public record ParsedPDF(String text, Map<String, String> metadata) {}
}