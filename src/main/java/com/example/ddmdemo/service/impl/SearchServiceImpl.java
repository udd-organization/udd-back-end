package com.example.ddmdemo.service.impl;

import com.example.ddmdemo.dto.IncidentReportDto;
import com.example.ddmdemo.model.IncidentReport;
import com.example.ddmdemo.modelIndex.IncidentReportIndex;
import com.example.ddmdemo.respository.IncidentReportRepository;
import com.example.ddmdemo.service.interfaces.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;

import java.util.*;

import co.elastic.clients.elasticsearch._types.query_dsl.*;
import org.elasticsearch.common.unit.Fuzziness;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final IncidentReportRepository incidentReportRepository;

    private static final Set<String> KEYWORD_FIELDS = Set.of(
            "severity"
    );

    private static final List<String> TEXT_FIELDS = List.of(
            "employee_full_name",
            "security_organization_name",
            "attacked_organization_name",
            "content"
    );

    private static final List<String> SEARCH_FIELDS = List.of(
            "employee_full_name^2",
            "security_organization_name",
            "attacked_organization_name",
            "content",
            "severity"
    );

    @Override
    public List<IncidentReportDto> search(List<String> keywords, String rawQuery, String typeOfSearch)
    {
        List<HighlightField> highlightFields = new ArrayList<>();

        highlightFields.add(new HighlightField("employee_full_name"));
        highlightFields.add(new HighlightField("security_organization_name"));
        highlightFields.add(new HighlightField("attacked_organization_name"));
        highlightFields.add(new HighlightField("severity"));
        highlightFields.add(new HighlightField("content"));


        HighlightParameters params = HighlightParameters.builder()
                .withPreTags("<em class=\"highlight\">")
                .withPostTags("</em>")
                .withRequireFieldMatch(false)
                .build();

        NativeQueryBuilder searchQueryBuilder = new NativeQueryBuilder()
                .withQuery(buildSimpleSearchQuery(keywords, rawQuery, typeOfSearch))
                .withHighlightQuery(new HighlightQuery(new Highlight(params, highlightFields), IncidentReportIndex.class)
                );

        return runQuery(searchQueryBuilder.build());
    }

    private Query buildSimpleSearchQuery(List<String> tokens, String rawQuery, String typeOfSearch){
        String trimmed = rawQuery != null ? rawQuery.trim() : "";
        boolean hasRaw = !trimmed.isBlank();
        boolean hasTokens = tokens != null && !tokens.isEmpty();

        switch(typeOfSearch){
            case "simple":
                return BoolQuery.of(q -> q.should(mb -> mb.bool(b -> {
                            tokens.forEach(token -> {
                                //fullNameAndSeverity
                                b.should(sb -> sb.match(m -> m.field("employee_full_name").fuzziness(Fuzziness.AUTO.asString()).query(token)));
                                b.should(sb -> sb.term(m -> m.field("severity").value(token.toUpperCase())));
                                //organizationsName
                                b.should(sb -> sb.match(m -> m.field("security_organization_name").fuzziness(Fuzziness.AUTO.asString()).query(token)));
                                b.should(sb -> sb.match(m -> m.field("attacked_organization_name").fuzziness(Fuzziness.AUTO.asString()).query(token)));
                                //content
                                b.should(sb -> sb.match(m -> m.field("content").fuzziness(Fuzziness.AUTO.asString()).query(token)));
                            });
                            return b;
                        })
                ))._toQuery();
            case "boolean":
                if (!hasRaw && !hasTokens) {
                    return QueryBuilders.matchAll(m -> m);
                }
                String raw = hasRaw ? trimmed : String.join(" ", tokens);

                BooleanDsl dsl = new BooleanDsl(KEYWORD_FIELDS, TEXT_FIELDS, SEARCH_FIELDS);

                return dsl.parseToQuery(raw);
            default:
                return null;
        }
    }

    private List<IncidentReportDto> runQuery(NativeQuery searchQuery) {
        var searchHits = elasticsearchOperations.search(
                searchQuery,
                IncidentReportIndex.class,
                IndexCoordinates.of("incident_report_index")
        );

        List<IncidentReportDto> result = new ArrayList<>();

        for(var hit : searchHits){
            IncidentReportIndex entity = hit.getContent();
            Optional<IncidentReport> incidentReport = incidentReportRepository.findById(UUID.fromString(entity.getDatabaseId()));
            IncidentReportDto dto = new IncidentReportDto(
                    entity.getEmployeeFullName(),
                    entity.getSecurityOrganizationName(),
                    entity.getAttackedOrganizationName(),
                    entity.getSeverity(),
                    "Address", //TODO: add address later
                    entity.getContent(),
                    incidentReport.get().getFilePath().replaceFirst("^incident-reports/", "")
            );

            //TODO: for location
            /*
            try {
                String[] location = entity.getLocation().split(",");
                dto.address = GeoPointCalculator.GetAddresFromGeoPoint(new GeoPoint(Double.parseDouble(location[0]), Double.parseDouble(location[1])));
            }catch (Exception e){
                e.printStackTrace();
            }
             */

            var highlights = hit.getHighlightFields();
            if(highlights != null && !highlights.isEmpty()){
                //replaceValueForField(dto, highlights);
                replaceValueForField(dto, highlights);
            }

            result.add(dto);
        }

        return result;
    }

    private void replaceValueForField(IncidentReportDto dto, Map<String, List<String>> highlights) {
        for (var entry : highlights.entrySet()) {
            String value = "";
            for (var valueEntry : entry.getValue()) {
                value += valueEntry;
            }

            switch (entry.getKey()) {
                case "employeeFullName":
                    dto.setEmployeeFullName(value);
                    break;
                case "securityOrganizationName":
                    dto.setSecurityOrganizationName(value);
                    break;
                case "attackedOrganizationName":
                    dto.setAttackedOrganizationName(value);
                    break;
                case "content":
                    dto.setContent(value);
                    break;
                case "severity":
                    dto.setSeverity(value);
                default:
                    break;
            }
        }
    }
}