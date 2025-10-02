package com.example.ddmdemo.service.impl;

import co.elastic.clients.elasticsearch._types.KnnQuery;
import com.example.ddmdemo.dto.IncidentReportDto;
import com.example.ddmdemo.model.IncidentReport;
import com.example.ddmdemo.modelIndex.IncidentReportIndex;
import com.example.ddmdemo.respository.IncidentReportRepository;
import com.example.ddmdemo.service.interfaces.GeocodingService;
import com.example.ddmdemo.service.interfaces.SearchService;
import com.example.ddmdemo.utils.VectorizationUtil;
import joptsimple.internal.Strings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import org.elasticsearch.common.unit.Fuzziness;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import org.json.JSONObject;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final IncidentReportRepository incidentReportRepository;
    private final GeocodingService geocodingService;
    private static final int RADIUS_METERS = 500;
    private static final String ORS_PROFILE = "foot-walking";  // ili "driving-car", "cycling-regular", ...

    @Value("${ors.api.key}")
    private String orsApiKey;

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
    public List<IncidentReportDto> search(List<String> keywords, String rawQuery, String searchType)
    {
        if(searchType.equals("geoSearch")){
            return geoSearch(keywords);
        }
        if ("knn".equals(searchType)) {
            return knnSearch(keywords);
        }

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
                .withQuery(buildSimpleSearchQuery(keywords, rawQuery, searchType))
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

    public List<IncidentReportDto> knnSearch(List<String> keywords) {
        try {
            String text = Strings.join(keywords, " ");

            float[] embedding = VectorizationUtil.getEmbedding(text);

            List<Float> vectorList = new ArrayList<>();
            for (float f : embedding) {
                vectorList.add(f);
            }

            //semanticka vrednost dokumenta (semanticka pretraga KNN)
            //trazi slicne vektore
            KnnQuery knnQuery = new KnnQuery.Builder()
                    .field("vectorizedContent")
                    .queryVector(vectorList)
                    .numCandidates(100)
                    .k(10)
                    .boost(10.0f)
                    .build();

            NativeQuery searchQuery = NativeQuery.builder()
                    .withKnnQuery(knnQuery)
                    .withMaxResults(5)
                    .withSearchType(null)
                    .build();

            var searchHits = elasticsearchOperations.search(searchQuery, IncidentReportIndex.class);

            List<IncidentReportDto> dtos = new ArrayList<>();
            for (var hit : searchHits) {
                IncidentReportIndex entity = hit.getContent();
                Optional<IncidentReport> incidentOpt = incidentReportRepository.findById(UUID.fromString(entity.getDatabaseId()));
                if (incidentOpt.isPresent()) {
                    IncidentReport incident = incidentOpt.get();
                    dtos.add(new IncidentReportDto(
                            entity.getEmployeeFullName(),
                            entity.getSecurityOrganizationName(),
                            entity.getAttackedOrganizationName(),
                            entity.getSeverity(),
                            incident.getAttackedOrganizationAddress(),
                            entity.getContent(),
                            incident.getFilePath().replaceFirst("^incident-reports/", "")
                    ));
                }
            }

            return dtos;

        } catch (Exception e) {
            log.error("KNN search failed", e);
            return List.of();
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
                    incidentReport.get().getAttackedOrganizationAddress(),
                    entity.getContent(),
                    incidentReport.get().getFilePath().replaceFirst("^incident-reports/", "")
            );

            try {
                dto.setAttackedOrganizationAddress(incidentReport.get().getAttackedOrganizationAddress());
            }catch (Exception e){
                e.printStackTrace();
            }

            var highlights = hit.getHighlightFields();
            if(highlights != null && !highlights.isEmpty()){
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

    public List<IncidentReportDto> geoSearch(List<String> keywords) {
        final String location = Strings.join(keywords, " ");
        System.out.println("[geoSearch] location=\"" + location + "\"");

        try {
            double[] geoPoint = geocodingService.getCoordinates(location);
            if (geoPoint == null || geoPoint.length < 2) {
                System.err.println("[geoSearch] Geocoding failed for: " + location);
                throw new RuntimeException("Could not geocode location: " + location);
            }
            double srcLat = geoPoint[0];
            double srcLon = geoPoint[1];
            System.out.println("[geoSearch] Start coordinates lat=" + srcLat + ", lon=" + srcLon);

            NativeQuery query = NativeQuery.builder()
                    .withQuery(Query.of(q -> q.matchAll(m -> m)))
                    .build();

            List<SearchHit<IncidentReportIndex>> searchHits =
                    elasticsearchOperations.search(query, IncidentReportIndex.class).getSearchHits();

            System.out.println("[geoSearch] ES docs fetched: " + searchHits.size());

            List<IncidentReportDto> dtos = new ArrayList<>();

            for (SearchHit<IncidentReportIndex> hit : searchHits) {
                IncidentReportIndex incident = hit.getContent();

                try {
                    // location je "lat,lon" string (po tvom index modelu)
                    String[] latLon = incident.getLocation().split(",");
                    if (latLon.length < 2) {
                        System.err.println("[geoSearch] Skip doc: bad location format: " + incident.getLocation());
                        continue;
                    }
                    double dstLat = Double.parseDouble(latLon[0].trim());
                    double dstLon = Double.parseDouble(latLon[1].trim());

                    double distanceMeters = getNetworkDistanceMeters(srcLat, srcLon, dstLat, dstLon, ORS_PROFILE);
                    System.out.println("[geoSearch] docId=" + incident.getDatabaseId()
                            + " distance=" + (int) distanceMeters + "m");

                    if (distanceMeters <= RADIUS_METERS) {
                        Optional<IncidentReport> incidentReportOpt =
                                incidentReportRepository.findById(UUID.fromString(incident.getDatabaseId()));

                        IncidentReportDto dto = new IncidentReportDto(
                                incident.getEmployeeFullName(),
                                incident.getSecurityOrganizationName(),
                                incident.getAttackedOrganizationName(),
                                incident.getSeverity(),
                                "<em class=\"highlight\">" + incidentReportOpt.map(IncidentReport::getAttackedOrganizationAddress).orElse("") + "</em>",
                                incident.getContent(),
                                incidentReportOpt.map(ir -> ir.getFilePath().replaceFirst("^incident-reports/", "")).orElse("")
                        );

                        dtos.add(dto);
                    }
                } catch (Exception perDocEx) {
                    System.err.println("[geoSearch] ERROR per-doc (dbId="
                            + incident.getDatabaseId() + "): " + perDocEx.getMessage());
                    perDocEx.printStackTrace();
                }
            }

            System.out.println("[geoSearch] Matched by network distance ≤ " + RADIUS_METERS + "m : " + dtos.size());
            return dtos;

        } catch (Exception e) {
            System.err.println("[geoSearch] ERROR: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * Vraća mrežnu distancu (u metrima) iz ORS directions API-ja između (srcLat,srcLon) i (dstLat,dstLon)
     * za dati profile (npr. "foot-walking", "driving-car").
     */
    private double getNetworkDistanceMeters(double srcLat, double srcLon,
                                            double dstLat, double dstLon,
                                            String profile) throws Exception {
        String url = String.format(
                "https://api.openrouteservice.org/v2/directions/%s?api_key=%s&start=%f,%f&end=%f,%f",
                profile,
                orsApiKey,
                srcLon, srcLat,       // start = lon,lat
                dstLon, dstLat        // end   = lon,lat
        );

        System.out.println("[ORS] URL = " + url);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/geo+json, application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        String body = response.body();

        System.out.println("[ORS] HTTP status = " + status);
        if (status < 200 || status >= 300) {
            System.err.println("[ORS] Non-2xx body (first 500 chars): " +
                    body.substring(0, Math.min(500, body.length())));
            throw new RuntimeException("ORS directions failed: HTTP " + status);
        }

        JSONObject json = new JSONObject(body);

        if (!json.has("features")) {
            System.err.println("[ORS] Unexpected body (no 'features'). First 500 chars: " +
                    body.substring(0, Math.min(500, body.length())));
            throw new RuntimeException("ORS: 'features' not present in response");
        }

        double distance = json.getJSONArray("features")
                .getJSONObject(0)
                .getJSONObject("properties")
                .getJSONArray("segments")
                .getJSONObject(0)
                .getDouble("distance");

        return distance;
    }
}