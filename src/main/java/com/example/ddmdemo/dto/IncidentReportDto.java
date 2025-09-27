package com.example.ddmdemo.dto;

import com.example.ddmdemo.enums.SeverityLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IncidentReportDto(
        @NotBlank(message = "Employee full name is required")
        @Size(max = 100, message = "Employee name must be at most 100 characters")
        String employeeFullName,

        @NotBlank(message = "Security organization name is required")
        @Size(max = 100)
        String securityOrganizationName,

        @NotBlank(message = "Attacked organization name is required")
        @Size(max = 100)
        String attackedOrganizationName,

        @NotNull(message = "Severity is required")
        SeverityLevel severity,

        @NotBlank(message = "Attacked organization address is required")
        @Size(max = 255)
        String attackedOrganizationAddress,

        String content
) {}