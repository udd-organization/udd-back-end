package com.example.ddmdemo.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.springframework.data.elasticsearch.client.erhlc.NativeSearchQuery;
import org.springframework.data.elasticsearch.client.erhlc.NativeSearchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;

public class SearchQueryBuilder {

    public void search(QueryBuilder query)
    {
        var searchQuery = new NativeSearchQueryBuilder()
                .withQuery(query)
                .withHighlightFields(
                        new HighlightBuilder.Field("incidentTitle").fragmentSize(200),
                        new HighlightBuilder.Field("description").fragmentSize(200),
                        new HighlightBuilder.Field("securityOrganization").fragmentSize(200),
                        new HighlightBuilder.Field("affectedOrganization").fragmentSize(200)).
                build();
    }

}
