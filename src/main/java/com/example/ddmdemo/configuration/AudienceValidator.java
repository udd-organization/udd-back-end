package com.example.ddmdemo.configuration;

import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;

import java.util.List;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {
    private final String requiredAudience;
    public AudienceValidator(String requiredAudience) { this.requiredAudience = requiredAudience; }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> aud = token.getAudience();
        if (aud != null && aud.contains(requiredAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Missing required audience: " + requiredAudience, null));
    }
}