package com.example.ddmdemo.utils;

import com.example.ddmdemo.model.IncidentReport;
import lombok.NoArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@NoArgsConstructor
public final class IncidentReportPdfRender {

    // Classpath locations for the embedded Unicode fonts
    private static final String FONT_REGULAR = "/fonts/dejavu-sans.book.ttf";
    private static final String FONT_BOLD    = "/fonts/dejavu-sans.bold.ttf";

    /** Public API: render EXACTLY like the original, with only the 5 fields changed. */
    public static byte[] render(IncidentReport report, String content) throws Exception {
        Parsed pair = parseDescAndNotes(content);
        String description     = pair.description;
        String additionalNotes = pair.notes;

        try (PDDocument doc = new PDDocument()) {

            // ✅ Load embedded Unicode fonts (supports Đ/đ/č/ć/š/ž)
            PDFont REGULAR = loadFont(doc, FONT_REGULAR);
            PDFont BOLD    = loadFont(doc, FONT_BOLD);

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float margin = 50f;
                float y = page.getMediaBox().getHeight() - margin;

                // Title (bold)
                y = writeLine(cs, "Incident Report", margin, y, BOLD, 16f, 22f);

                // EXACT labels, no "ID", no "Content"
                y = writeLine(cs, "Employee Name: " + safe(report.getEmployeeFullName()),
                        margin, y, REGULAR, 12f, 16f);

                y = writeLine(cs, "Security Organization: " + safe(report.getSecurityOrganizationName()),
                        margin, y, REGULAR, 12f, 16f);

                y = writeLine(cs, "Attacked Organization: " + safe(report.getAttackedOrganizationName()),
                        margin, y, REGULAR, 12f, 16f);

                y = writeLine(cs, "Address: " + safe(report.getAttackedOrganizationAddress()),
                        margin, y, REGULAR, 12f, 16f);

                y = writeLine(cs, "Severity: " + (report.getSeverity() != null ? report.getSeverity().name() : ""),
                        margin, y, REGULAR, 12f, 20f);

                // Description / Additional Notes
                float maxWidth = PDRectangle.A4.getWidth() - 2 * margin;
                y = writeWrapped(cs, "Description: " + safe(description),
                        margin, y, REGULAR, 12f, maxWidth);

                y = writeWrapped(cs, "Additional Notes: " + safe(additionalNotes),
                        margin, y, REGULAR, 12f, maxWidth);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    // ---------- fonts ----------

    private static PDFont loadFont(PDDocument doc, String classpath) throws Exception {
        try (InputStream is = IncidentReportPdfRender.class.getResourceAsStream(classpath)) {
            if (is == null) {
                throw new IllegalStateException("Font not found on classpath: " + classpath +
                        " (place it under src/main/resources" + classpath + ")");
            }
            // Embed as Unicode font
            return PDType0Font.load(doc, is, true);
        }
    }

    // ---------- helpers ----------

    private static String safe(String s) { return s == null ? "" : s; }

    private static float writeLine(PDPageContentStream cs, String text, float x, float y,
                                   PDFont font, float size, float leading) throws Exception {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
        return y - leading;
    }

    private static float writeWrapped(PDPageContentStream cs, String text, float x, float y,
                                      PDFont font, float size, float maxWidth) throws Exception {
        final float leading = size * 1.4f;
        String content = text.replace("\r", "");
        String[] words = content.split("\\s+");

        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);

        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String test = line.length() == 0 ? w : line + " " + w;
            float wpx = font.getStringWidth(test) / 1000f * size; // works for PDType0Font
            if (wpx > maxWidth) {
                cs.showText(line.toString());
                cs.newLineAtOffset(0, -leading);
                y -= leading;
                line.setLength(0);
                line.append(w);
            } else {
                line.setLength(0);
                line.append(test);
            }
        }
        if (line.length() > 0) {
            cs.showText(line.toString());
            y -= leading;
        }
        cs.endText();
        return y;
    }

    /** Tiny struct (no Java record to avoid accessor issues). */
    private static final class Parsed {
        final String description;
        final String notes;
        Parsed(String d, String n) { this.description = d; this.notes = n; }
    }

    /**
     * Extract only "Description:" and "Additional Notes:" from the incoming string.
     * Accepts inputs like:
     *   "Incident Report\nDescription: ...\nAdditional Notes: ...",
     *   "Description: ... Additional Notes: ...", or even just
     *   the whole string as Description.
     */
    private static Parsed parseDescAndNotes(String raw) {
        if (raw == null) return new Parsed("", "");
        String s = raw.replace("\r", "");

        // Strip an optional leading "Incident Report" or "Content:" line
        s = s.replaceFirst("(?is)^\\s*(Incident\\s*Report\\s*\\n+|Content:\\s*)", "");

        String desc = "";
        String notes = "";

        var mDesc = java.util.regex.Pattern
                .compile("(?is)Description:\\s*(.*?)(?:\\R+Additional\\s*Notes:|$)")
                .matcher(s);
        if (mDesc.find()) desc = mDesc.group(1).trim();

        var mNotes = java.util.regex.Pattern
                .compile("(?is)Additional\\s*Notes:\\s*(.*)$")
                .matcher(s);
        if (mNotes.find()) notes = mNotes.group(1).trim();

        if (desc.isEmpty() && notes.isEmpty()) desc = s.trim();
        return new Parsed(desc, notes);
    }
}