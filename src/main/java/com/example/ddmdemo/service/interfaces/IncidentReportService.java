package com.example.ddmdemo.service.interfaces;

import com.example.ddmdemo.model.IncidentReport;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface IncidentReportService {
    List<IncidentReport> getAll();
    IncidentReport getById(UUID id);
    IncidentReport create(IncidentReport incidentReport);
    String uploadFileToMinio(MultipartFile file);
    IncidentReport update(UUID id, IncidentReport incidentReport);
    void delete(UUID id);
    IncidentReport createIncidentReport(MultipartFile pdfFile, IncidentReport report, String content);
}