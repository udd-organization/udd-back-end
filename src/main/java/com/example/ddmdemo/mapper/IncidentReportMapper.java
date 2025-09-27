package com.example.ddmdemo.mapper;

import com.example.ddmdemo.dto.IncidentReportDto;
import com.example.ddmdemo.model.IncidentReport;

public class IncidentReportMapper {

    public static IncidentReport toEntity(IncidentReportDto dto) {
        IncidentReport entity = new IncidentReport();
        entity.setEmployeeFullName(dto.employeeFullName());
        entity.setSecurityOrganizationName(dto.securityOrganizationName());
        entity.setAttackedOrganizationName(dto.attackedOrganizationName());
        entity.setSeverity(dto.severity());
        entity.setAttackedOrganizationAddress(dto.attackedOrganizationAddress());
        return entity;
    }

    public static IncidentReportDto toDto(IncidentReport entity) {
        return new IncidentReportDto(
                entity.getEmployeeFullName(),
                entity.getSecurityOrganizationName(),
                entity.getAttackedOrganizationName(),
                entity.getSeverity(),
                entity.getAttackedOrganizationAddress(),
                ""
        );
    }
}