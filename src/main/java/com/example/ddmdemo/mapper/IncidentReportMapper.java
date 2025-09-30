package com.example.ddmdemo.mapper;

import com.example.ddmdemo.dto.IncidentReportDto;
import com.example.ddmdemo.enums.SeverityLevel;
import com.example.ddmdemo.model.IncidentReport;

import java.util.List;

public class IncidentReportMapper {

    public static IncidentReport toEntity(IncidentReportDto dto) {
        IncidentReport entity = new IncidentReport();
        entity.setEmployeeFullName(dto.getEmployeeFullName());
        entity.setSecurityOrganizationName(dto.getSecurityOrganizationName());
        entity.setAttackedOrganizationName(dto.getAttackedOrganizationName());
        entity.setSeverity(SeverityLevel.valueOf(dto.getSeverity()));
        entity.setAttackedOrganizationAddress(dto.getAttackedOrganizationAddress());
        return entity;
    }

    public static IncidentReportDto toDto(IncidentReport entity) {
        return new IncidentReportDto(
                entity.getEmployeeFullName(),
                entity.getSecurityOrganizationName(),
                entity.getAttackedOrganizationName(),
                entity.getSeverity().toString(),
                entity.getAttackedOrganizationAddress(),
                "",
                ""
        );
    }

    public static List<IncidentReportDto> toDtoList(List<IncidentReport> incidents) {
        return incidents.stream()
                .map(IncidentReportMapper::toDto)
                .toList();
    }
}