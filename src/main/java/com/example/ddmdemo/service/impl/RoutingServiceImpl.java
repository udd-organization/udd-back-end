/*package com.example.ddmdemo.service.impl;

import com.example.ddmdemo.service.interfaces.RoutingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class RoutingServiceImpl {
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${ors.api.key}")
    private String apiKey;

    public RoutingServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }


    public String getIsochronePolygon(double lon, double lat, int rangeMeters) {
        String url = "https://api.openrouteservice.org/v2/isochrones/driving-car";

        Map<String, Object> body = Map.of(
                "locations", List.of(List.of(lon, lat)),
                "range_type", "distance",
                "range", List.of(rangeMeters)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", apiKey);

        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
                url, HttpMethod.POST, httpEntity, Map.class
        );

        Map<?, ?> respBody = resp.getBody();
        if (respBody == null) throw new RuntimeException("Empty ORS response");

        List<Map<String, Object>> features = (List<Map<String, Object>>) respBody.get("features");
        if (features == null || features.isEmpty()) throw new RuntimeException("ORS returned no features");

        Map<String, Object> geometry = (Map<String, Object>) features.get(0).get("geometry");
        try {
            return mapper.writeValueAsString(geometry);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize geometry", e);
        }
    }
}
*/