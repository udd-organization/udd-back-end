package com.example.ddmdemo.service.impl;

import com.example.ddmdemo.model.IncidentReport;
import com.example.ddmdemo.modelIndex.IncidentReportIndex;
import com.example.ddmdemo.repositoryIndex.IncidentReportIndexRepository;
import com.example.ddmdemo.respository.IncidentReportRepository;
import com.example.ddmdemo.service.interfaces.GeocodingService;
import com.example.ddmdemo.service.interfaces.IncidentReportService;
import com.example.ddmdemo.utils.IncidentReportPdfRender;
import com.example.ddmdemo.utils.VectorizationUtil;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;

@Service
public class IncidentReportServiceImpl implements IncidentReportService {

    private final IncidentReportRepository incidentReportRepository;
    private final IncidentReportIndexRepository incidentReportIndexRepository;
    private final MinioClient minioClient;
    private final GeocodingService geocodingService;

    public IncidentReportServiceImpl(IncidentReportRepository incidentReportRepository,
                                     IncidentReportIndexRepository incidentReportIndexRepository,
                                     MinioClient minioClient,
                                     GeocodingService geocodingService) {
        this.incidentReportRepository = incidentReportRepository;
        this.incidentReportIndexRepository = incidentReportIndexRepository;
        this.minioClient = minioClient;
        this.geocodingService = geocodingService;
    }

    @Override
    public List<IncidentReport> getAll() {
        return incidentReportRepository.findAll();
    }

    @Override
    public IncidentReport getById(UUID id) {
        return incidentReportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("IncidentReport not found with id: " + id));
    }

    @Override
    public IncidentReport create(IncidentReport incidentReport) {
        return incidentReportRepository.save(incidentReport);
    }

    //TODO: refactor - put into FileServiceMinioImpl
    @Override
    public String uploadFileToMinio(MultipartFile file) {
        try {
            String objectName = "incident-reports/" + file.getOriginalFilename();
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket("security-incidents")
                                .object(objectName)
                                .stream(inputStream, file.getSize(), -1)
                                .contentType(file.getContentType())
                                .build()
                );
            }
            return objectName;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to MinIO", e);
        }
    }

    private String uploadFileToMinio(byte[] bytes, String objectName, String contentType) {
        try (InputStream is = new java.io.ByteArrayInputStream(bytes)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket("security-incidents")
                            .object(objectName)
                            .stream(is, bytes.length, -1)
                            .contentType(contentType != null ? contentType : "application/pdf")
                            .build()
            );
            return objectName;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to MinIO", e);
        }
    }

    @Override
    public IncidentReport update(UUID id, IncidentReport updatedReport) {
        IncidentReport existing = getById(id);
        existing.setEmployeeFullName(updatedReport.getEmployeeFullName());
        existing.setSecurityOrganizationName(updatedReport.getSecurityOrganizationName());
        existing.setAttackedOrganizationName(updatedReport.getAttackedOrganizationName());
        existing.setSeverity(updatedReport.getSeverity());
        existing.setAttackedOrganizationAddress(updatedReport.getAttackedOrganizationAddress());
        return incidentReportRepository.save(existing);
    }

    @Override
    public void delete(UUID id) {
        incidentReportRepository.deleteById(id);
    }

    @Override
    @Transactional
    public IncidentReport createIncidentReport(MultipartFile originalFile,
                                               IncidentReport report,
                                               String content) {
        byte[] pdfBytes;
        try {
            pdfBytes = IncidentReportPdfRender.render(report, content);
        } catch (Exception e) {
            throw new RuntimeException("Failed to render PDF", e);
        }

        String objectName = "incident-reports/" + originalFile.getOriginalFilename();
        uploadFileToMinio(pdfBytes, objectName, "application/pdf");

        report.setFilePath(objectName);
        IncidentReport saved = create(report);

        StringBuilder fullText = new StringBuilder();

        if (saved.getEmployeeFullName() != null) {
            fullText.append(saved.getEmployeeFullName()).append(" ");
        }
        if (saved.getSecurityOrganizationName() != null) {
            fullText.append(saved.getSecurityOrganizationName()).append(" ");
        }
        if (saved.getAttackedOrganizationName() != null) {
            fullText.append(saved.getAttackedOrganizationName()).append(" ");
        }
        if (saved.getSeverity() != null) {
            fullText.append(saved.getSeverity()).append(" ");
        }
        if (content != null) {
            fullText.append(content).append(" ");
        }
        if (saved.getAttackedOrganizationAddress() != null) {
            fullText.append(saved.getAttackedOrganizationAddress()).append(" ");
        }

        String textForEmbedding = fullText.toString().trim();

        float[] vectorizedContent;
        try {
            vectorizedContent = VectorizationUtil.getEmbedding(textForEmbedding);
        } catch (Exception e) {
            throw new RuntimeException("Failed to vectorize content", e);
        }

        IncidentReportIndex ix = new IncidentReportIndex();
        ix.setEmployeeFullName(saved.getEmployeeFullName());
        ix.setSecurityOrganizationName(saved.getSecurityOrganizationName());
        ix.setAttackedOrganizationName(saved.getAttackedOrganizationName());
        ix.setSeverity(saved.getSeverity().toString());
        ix.setDatabaseId(saved.getId().toString());
        ix.setContent(content != null ? content : "");
        String address = saved.getAttackedOrganizationAddress();
        String city = null;
        if (address != null && address.contains(",")) {
            city = address.substring(address.lastIndexOf(",") + 1).trim();
        }
        ix.setCity(city);
        double[] coords = geocodingService.getCoordinates(address);
        if (coords != null) {
            double lat = coords[0];
            double lon = coords[1];
            ix.setLocation(lat + "," + lon);
        }
        ix.setVectorizedContent(vectorizedContent);
        incidentReportIndexRepository.save(ix);

        logToFile(saved, content != null ? content : "");
        return saved;
    }

    private void logToFile(IncidentReport report, String content){
        double lat;
        double lon;

        try {
            double[] coords = geocodingService.getCoordinates(report.getAttackedOrganizationAddress());
            if (coords == null) {
                throw new RuntimeException("Geocoding returned null for address: " + report.getAttackedOrganizationAddress());
            }
            lat = coords[0];
            lon = coords[1];
        } catch (Exception e) {
            throw new RuntimeException("Could not resolve coordinates for: " + report.getAttackedOrganizationAddress(), e);
        }

        String address = report.getAttackedOrganizationAddress().split(", ")[0];
        String city = report.getAttackedOrganizationAddress().split(", ")[1];

        String log = String.format("[%s] IncidentReport saved: " +
                        "id=%s, " +
                        "employee=%s, " +
                        "securityOrg=%s, " +
                        "attackedOrg=%s, " +
                        "severity=%s, " +
                        "attackedOrgAddress=%s, " +
                        "attackedOrgCity=%s, " +
                        "latitude=%s, " +
                        "longitude=%s, " +
                        "content=%s, " +
                        "file=%s%n",
                java.time.LocalDateTime.now(),
                report.getId(),
                report.getEmployeeFullName(),
                report.getSecurityOrganizationName(),
                report.getAttackedOrganizationName(),
                report.getSeverity(),
                address,
                city,
                lat,
                lon,
                content,
                report.getFilePath()
        );

        try {
            String projectRoot = System.getProperty("user.dir");
            Path logDir = Paths.get(projectRoot, "elk", "logstash", "logstash-ingest-data");
            Files.createDirectories(logDir);

            Path logFilePath = logDir.resolve("application.log");

            Files.write(logFilePath, (log + System.lineSeparator()).getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}