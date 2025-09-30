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
            String text = normalizeForIndexing(raw); // normalized, still full text

            // --- Extract & strip fields from the text (remove matched lines) ---
            ExtractResult rEmployee = extractAndStrip(
                    text,
                    Pattern.compile("(?im)^\\s*employee\\s*(full\\s*)?name\\s*[:\\-]\\s*(.+)\\s*$"),
                    2
            );
            text = rEmployee.text();
            String employeeFullName = rEmployee.value();

            ExtractResult rSecOrg = extractAndStrip(
                    text,
                    Pattern.compile("(?im)^\\s*security\\s*organization\\s*[:\\-]\\s*(.+)\\s*$"),
                    1
            );
            text = rSecOrg.text();
            String securityOrganization = rSecOrg.value();

            ExtractResult rAttackedOrg = extractAndStrip(
                    text,
                    Pattern.compile("(?im)^\\s*(attacked|victim|target)\\s*organization\\s*[:\\-]\\s*(.+)\\s*$"),
                    2
            );
            text = rAttackedOrg.text();
            String attackedOrganization = rAttackedOrg.value();

            // address/location may appear in a few ways; grab the value part and strip the whole line
            ExtractResult rAddress = extractAndStrip(
                    text,
                    Pattern.compile("(?im)^\\s*(?:attacked\\s*organization\\s*)?(address|location)\\s*[:\\-]\\s*(.+)\\s*$"),
                    2
            );
            text = rAddress.text();
            String attackedOrgAddress = rAddress.value();

            ExtractResult rSeverity = extractAndStrip(
                    text,
                    Pattern.compile("(?im)^\\s*severity\\s*[:\\-]\\s*(LOW|MEDIUM|HIGH|CRITICAL)\\b.*$"),
                    1
            );
            text = rSeverity.text();
            SeverityLevel severity = parseSeverity(rSeverity.value());

            // Final cleanup after removals (collapse blank lines etc.)
            String remainingContent = tidyAfterStrips(text);

            return new IncidentReportDto(
                    nz(employeeFullName),
                    nz(securityOrganization),
                    nz(attackedOrganization),
                    (severity != null ? severity.toString() : SeverityLevel.LOW.toString()),
                    nz(attackedOrgAddress),
                    remainingContent,
                    ""
            );

        } catch (IOException | SAXException | TikaException e) {
            throw new RuntimeException("Failed to parse PDF", e);
        }
    }

    // --- Helpers ---

    private static String normalizeForIndexing(String s) {
        if (s == null) return "";
        // de-hyphenate words broken at line ends: "bezbedno-\nsti" -> "bezbednosti"
        String t = s.replaceAll("(\\p{L})-\\R(\\p{L})", "$1$2");
        // normalize spaces
        t = t.replace("\u00A0", " "); // NBSP
        t = t.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        // keep paragraph breaks but avoid huge blocks of newlines
        t = t.replaceAll("\\R{2,}", "\n");
        return t.trim();
    }

    private static String tidyAfterStrips(String s) {
        String t = s;
        // Remove pure-whitespace lines
        t = t.replaceAll("(?m)^[ \\t]+$", "");
        // Collapse 3+ newlines to max 2 to keep some paragraph separation
        t = t.replaceAll("\\n{3,}", "\n\n");
        return t.trim();
    }

    private record ExtractResult(String value, String text) {}

    /**
     * Extract value by regex and remove the matched line/segment from text.
     * @param text full text to search in
     * @param pattern compiled pattern with capturing groups
     * @param valueGroup which group holds the value (1-based)
     */
    private ExtractResult extractAndStrip(String text, Pattern pattern, int valueGroup) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) {
            return new ExtractResult(null, text);
        }
        String value = m.group(valueGroup);
        // remove matched region
        String newText = text.substring(0, m.start()) + text.substring(m.end());
        return new ExtractResult(nz(value), newText);
    }

    // Kept in case you want to reuse elsewhere
    @SuppressWarnings("unused")
    private String find(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (!m.find()) return null;
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

    // --- importPDF remains unchanged, returns full content/metadata (no stripping) ---

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