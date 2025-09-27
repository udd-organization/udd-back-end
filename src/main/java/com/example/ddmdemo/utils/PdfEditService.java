/*package com.example.ddmdemo.utils;

import com.example.ddmdemo.dto.IncidentReportDto;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class PdfEditService {

    public byte[] applyFieldsToPdf(byte[] originalPdf, IncidentReportDto dto) {
        try (PDDocument doc = PDDocument.load(originalPdf)) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
            boolean wrote = false;

            if (form != null && !form.getFields().isEmpty()) {
                wrote |= setIfPresent(form, List.of("employeeFullName","employee_name","full_name"),
                        dto.getEmployeeFullName());
                wrote |= setIfPresent(form, List.of("securityOrganizationName","security_org"),
                        dto.getSecurityOrganizationName());
                wrote |= setIfPresent(form, List.of("attackedOrganizationName","victim_org","target_org"),
                        dto.getAttackedOrganizationName());
                wrote |= setIfPresent(form, List.of("attackedOrganizationAddress","address"),
                        dto.getAttackedOrganizationAddress());
                wrote |= setIfPresent(form, List.of("severity","incident_severity"),
                        dto.getSeverity() == null ? null : dto.getSeverity().name());

                if (wrote) {
                    form.flatten();
                }
            }

            if (!wrote) {
                // stamp a small metadata block at the bottom of the first page
                PDPage page = doc.getNumberOfPages() > 0 ? doc.getPage(0) : new PDPage();
                if (doc.getNumberOfPages() == 0) doc.addPage(page);

                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                    cs.setFont(PDType1Font.HELVETICA, 10);
                    cs.beginText();
                    cs.newLineAtOffset(36, 60);
                    cs.showText("=== Incident Metadata (system) ==="); cs.newLineAtOffset(0, 12);
                    writeLine(cs, "Employee: ", dto.getEmployeeFullName());
                    writeLine(cs, "Security Org: ", dto.getSecurityOrganizationName());
                    writeLine(cs, "Attacked Org: ", dto.getAttackedOrganizationName());
                    writeLine(cs, "Address: ", dto.getAttackedOrganizationAddress());
                    writeLine(cs, "Severity: ", dto.getSeverity() == null ? null : dto.getSeverity().name());
                    cs.endText();
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to update PDF with edited fields", e);
        }
    }

    private boolean setIfPresent(PDAcroForm form, List<String> names, String value) throws IOException {
        if (value == null) return false;
        for (String n : names) {
            PDField f = form.getField(n);
            if (f != null) { f.setValue(value); return true; }
        }
        return false;
    }

    private void writeLine(PDPageContentStream cs, String label, String value) throws IOException {
        if (value != null && !value.isBlank()) {
            cs.showText(label + value); cs.newLineAtOffset(0, 12);
        }
    }
}

 */
