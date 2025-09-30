package com.example.ddmdemo.service.interfaces;

import java.util.List;

import com.example.ddmdemo.dto.IncidentReportDto;
import com.example.ddmdemo.model.IncidentReport;
import org.springframework.stereotype.Service;

@Service
public interface SearchService {
    List<IncidentReportDto> search(List<String> keywords, String type);
}