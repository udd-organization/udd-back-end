package com.example.ddmdemo.repositoryIndex;

import com.example.ddmdemo.modelIndex.IncidentReportIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidentReportIndexRepository extends ElasticsearchRepository<IncidentReportIndex, String> {
}