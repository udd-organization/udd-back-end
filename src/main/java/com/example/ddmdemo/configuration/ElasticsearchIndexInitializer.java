package com.example.ddmdemo.configuration;

import com.example.ddmdemo.modelIndex.IncidentReportIndex;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;

@Configuration
public class ElasticsearchIndexInitializer {
    private final ElasticsearchOperations elasticsearchOperations;

    public ElasticsearchIndexInitializer(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @PostConstruct
    public void setupIndex() {
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(IncidentReportIndex.class);
            if (!indexOps.exists()) {
                System.out.println("Creating index...");
                indexOps.create();
                System.out.println("Creating mapping...");
                indexOps.putMapping(indexOps.createMapping());
            } else {
                System.out.println("Index already exists.");
            }
        } catch (Exception e) {
            System.err.println("🔥 Index setup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}