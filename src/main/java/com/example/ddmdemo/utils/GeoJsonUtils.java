package com.example.ddmdemo.utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.List;

public class GeoJsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Vrati spoljašnji prsten prvog poligona kao listu [lon, lat] tačaka.
     */
    public static List<double[]> extractOuterRing(String geometryJson) {
        try {
            JsonNode root = MAPPER.readTree(geometryJson);
            String type = root.get("type").asText();
            ArrayNode coords = (ArrayNode) root.get("coordinates");

            if ("Polygon".equals(type)) {
                ArrayNode outer = (ArrayNode) coords.get(0);
                return toLonLatList(outer);
            } else if ("MultiPolygon".equals(type)) {
                ArrayNode firstPolyOuter = (ArrayNode) coords.get(0).get(0);
                return toLonLatList(firstPolyOuter);
            } else {
                throw new IllegalArgumentException("Unsupported geometry type: " + type);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse isochrone geometry", e);
        }
    }

    private static List<double[]> toLonLatList(ArrayNode arr) {
        List<double[]> out = new ArrayList<>(arr.size());
        for (JsonNode n : arr) {
            double lon = n.get(0).asDouble();
            double lat = n.get(1).asDouble();
            out.add(new double[]{lon, lat});
        }
        return out;
    }
}