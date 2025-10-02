package com.example.ddmdemo.modelIndex;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.elasticsearch.annotations.*;

@Document(indexName = "incident_report_index")
@Setting(settingPath = "configuration/serbian-analyzer-config.json")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class IncidentReportIndex {
    @Id
    private String id;

    @MultiField(
            mainField = @Field(
                    name = "employee_full_name",
                    type = FieldType.Text,
                    store = true,
                    analyzer = "serbian_simple",
                    searchAnalyzer = "serbian_simple"
            ),
            otherFields = {
                    @InnerField(
                            suffix = "keyword",
                            type = FieldType.Keyword
                    )
            }
    )
    private String employeeFullName;

    @Field(type = FieldType.Text, store = true, name = "security_organization_name", analyzer = "serbian_simple", searchAnalyzer = "serbian_simple")
    private String securityOrganizationName;

    @MultiField(
            mainField = @Field(
                    name = "attacked_organization_name",
                    type = FieldType.Text,
                    store = true,
                    analyzer = "serbian_simple",
                    searchAnalyzer = "serbian_simple"
            ),
            otherFields = {
                    @InnerField(
                            suffix = "keyword",
                            type = FieldType.Keyword
                    )
            }
    )
    private String attackedOrganizationName;

    @Field(type = FieldType.Keyword, store = true, name = "severity")
    private String severity;

    @Field(type = FieldType.Text, store = true, name = "database_id")
    private String databaseId;

    @Field(type = FieldType.Text, store = true, name = "content", analyzer = "serbian_simple", searchAnalyzer = "serbian_simple")
    private String content;

    @GeoPointField
    private String location;

    @Field(type = FieldType.Keyword, store = true)
    private String city;

    @Field(type = FieldType.Dense_Vector, dims = 384, similarity = "cosine")
    private float[] vectorizedContent;
}