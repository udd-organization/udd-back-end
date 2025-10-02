package com.example.ddmdemo.service.interfaces;

import org.springframework.stereotype.Service;

@Service
public interface GeocodingService {
    public double[] getCoordinates(String location);
}