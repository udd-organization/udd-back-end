package com.example.ddmdemo.controller;

import com.example.ddmdemo.dto.IncidentReportDto;
import com.example.ddmdemo.dto.SearchQueryDto;
import com.example.ddmdemo.mapper.IncidentReportMapper;
import com.example.ddmdemo.model.IncidentReport;
import com.example.ddmdemo.service.interfaces.IncidentReportService;
import com.example.ddmdemo.service.interfaces.SearchService;
import com.example.ddmdemo.utils.PdfParserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/incident-reports")
@RequiredArgsConstructor
public class IncidentReportController {

    private final IncidentReportService incidentReportService;
    private final PdfParserService pdfParserService;
    private final SearchService searchService;

    @GetMapping
    public ResponseEntity<List<IncidentReportDto>> getAll() {
        List<IncidentReportDto> dtos = incidentReportService.getAll()
                .stream()
                .map(IncidentReportMapper::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentReportDto> getById(@PathVariable UUID id) {
        IncidentReport report = incidentReportService.getById(id);
        return ResponseEntity.ok(IncidentReportMapper.toDto(report));
    }

    @PostMapping
    public ResponseEntity<IncidentReportDto> create(@Valid @RequestBody IncidentReportDto dto) {
        IncidentReport report = IncidentReportMapper.toEntity(dto);
        IncidentReport saved = incidentReportService.create(report);
        return ResponseEntity.ok(IncidentReportMapper.toDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<IncidentReportDto> update(@PathVariable UUID id,
                                                    @Valid @RequestBody IncidentReportDto dto) {
        IncidentReport report = IncidentReportMapper.toEntity(dto);
        IncidentReport updated = incidentReportService.update(id, report);
        return ResponseEntity.ok(IncidentReportMapper.toDto(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        incidentReportService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/upload/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IncidentReportDto> parseOnly(
            @RequestPart("file") MultipartFile file) {
        IncidentReportDto parsed = pdfParserService.parse(file);
        return ResponseEntity.ok(parsed);
    }

    /**
     * Upload a PDF + incident metadata, save in DB, MinIO, and Elasticsearch.
     */
    @PostMapping(value = "/upload/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IncidentReportDto> uploadIncidentReport(
            @RequestPart("file") MultipartFile pdfFile,
            @Valid @RequestPart("metadata") IncidentReportDto dto) {
            IncidentReport report = IncidentReportMapper.toEntity(dto);
            IncidentReport savedIncident = incidentReportService.createIncidentReport(pdfFile, report, dto.getContent());

            return ResponseEntity.ok(IncidentReportMapper.toDto(savedIncident));
    }

    @PostMapping("/search/{searchType}")
    public ResponseEntity<List<IncidentReportDto>> search(
            @RequestBody SearchQueryDto searchQueryDto,
            @PathVariable String searchType) {
        return ResponseEntity.ok(searchService.search(searchQueryDto.keywords(), searchQueryDto.rawQuery(), searchType));
    }
}