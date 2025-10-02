package com.example.ddmdemo.service.impl;

import com.example.ddmdemo.service.interfaces.GeocodingService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

@Service
public class GeocodingServiceImpl implements GeocodingService {

    private final RestTemplate restTemplate;

    public GeocodingServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public double[] getCoordinates(String location) {
        try {
            String urlStr = "https://nominatim.openstreetmap.org/search?q="
                    + URLEncoder.encode(location, StandardCharsets.UTF_8)
                    + "&format=json&limit=1&accept-language=sr-Latn";

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // ✅ Required header for Nominatim
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Accept", "application/json");
            conn.connect();

            // Read response
            StringBuilder inline = new StringBuilder();
            try (Scanner sc = new Scanner(conn.getInputStream())) {
                while (sc.hasNext()) {
                    inline.append(sc.nextLine());
                }
            }

            JSONArray jsonArray = new JSONArray(inline.toString());
            if (jsonArray.length() > 0) {
                JSONObject locationObj = jsonArray.getJSONObject(0);
                double lat = Double.parseDouble(locationObj.getString("lat"));
                double lon = Double.parseDouble(locationObj.getString("lon"));
                return new double[]{lat, lon};
            }

            throw new RuntimeException("Nominatim returned no results for: " + location);
        } catch (IOException e) {
            throw new RuntimeException("Error calling Nominatim for: " + location, e);
        }
    }
}