package com.undoschool.demo.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggest;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggestOption;
import co.elastic.clients.json.JsonData;
import com.undoschool.demo.dto.CourseSearchRequest;
import com.undoschool.demo.dto.CourseSearchResponse;
import com.undoschool.demo.model.CourseDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseSearchService {

    private static final String SUGGESTER_NAME = "title_suggest";
    private static final String DEFAULT_SORT = "upcoming";
    private static final int DEFAULT_SUGGESTION_SIZE = 10;

    private final ElasticsearchClient elasticsearchClient;

    @Value("${app.elasticsearch.index.courses}")
    private String coursesIndex;

    public CourseSearchResponse searchCourses(CourseSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        try {
            SearchRequest searchRequest = buildSearchRequest(request);
            SearchResponse<CourseDocument> response = elasticsearchClient.search(searchRequest, CourseDocument.class);
            return buildSearchResponse(response, request);
        } catch (Exception e) {
            log.error("Error searching courses with request: {}", request, e);
            throw new RuntimeException("Failed to search courses", e);
        }
    }

    public List<String> getSuggestions(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            SearchResponse<CourseDocument> response = elasticsearchClient.search(
                    buildSuggestionRequest(query),
                    CourseDocument.class
            );
            return extractSuggestions(response);
        } catch (Exception e) {
            log.error("Error getting suggestions for query: {}", query, e);
            return Collections.emptyList();
        }
    }

    private SearchRequest buildSuggestionRequest(String query) {
        return SearchRequest.of(s -> s
                .index(coursesIndex)
                .suggest(suggest -> suggest
                        .suggesters(SUGGESTER_NAME, suggester -> suggester
                                .prefix(query)
                                .completion(completion -> completion
                                        .field("suggest")
                                        .size(DEFAULT_SUGGESTION_SIZE)
                                )
                        )
                )
        );
    }

    private List<String> extractSuggestions(SearchResponse<CourseDocument> response) {
        return Optional.ofNullable(response.suggest())
                .map(s -> s.get(SUGGESTER_NAME))
                .orElse(Collections.emptyList())
                .stream()
                .filter(s -> s.isCompletion())
                .flatMap(s -> s.completion().options().stream())
                .map(CompletionSuggestOption::text)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private SearchRequest buildSearchRequest(CourseSearchRequest request) {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();
        addSearchQuery(request, boolQuery);
        addAgeFilters(request, boolQuery);
        addCategoryFilter(request, boolQuery);
        addTypeFilter(request, boolQuery);
        addPriceFilters(request, boolQuery);
        addDateFilter(request, boolQuery);

        return SearchRequest.of(s -> s
                .index(coursesIndex)
                .query(boolQuery.build()._toQuery())
                .sort(buildSort(request.getSort()))
                .from(request.getPage() * request.getSize())
                .size(request.getSize())
        );
    }

    private void addSearchQuery(CourseSearchRequest request, BoolQuery.Builder boolQuery) {
        if (request.getQ() != null && !request.getQ().trim().isEmpty()) {
            boolQuery.must(Query.of(q -> q
                    .multiMatch(m -> m
                            .query(request.getQ())
                            .fields("title^2", "description")
                            .fuzziness("AUTO")
                    )
            ));
        }
    }

    private void addAgeFilters(CourseSearchRequest request, BoolQuery.Builder boolQuery) {
        if (request.getMinAge() != null) {
            boolQuery.filter(Query.of(q -> q
                    .range(RangeQuery.of(r -> r
                            .field("maxAge")
                            .gte(JsonData.of(request.getMinAge()))
                    ))
            ));
        }

        if (request.getMaxAge() != null) {
            boolQuery.filter(Query.of(q -> q
                    .range(RangeQuery.of(r -> r
                            .field("minAge")
                            .lte(JsonData.of(request.getMaxAge()))
                    ))
            ));
        }
    }

    private void addCategoryFilter(CourseSearchRequest request, BoolQuery.Builder boolQuery) {
        if (request.getCategory() != null) {
            boolQuery.filter(Query.of(q -> q
                    .term(t -> t
                            .field("category")
                            .value(request.getCategory())
                    )
            ));
        }
    }

    private void addTypeFilter(CourseSearchRequest request, BoolQuery.Builder boolQuery) {
        if (request.getType() != null) {
            boolQuery.filter(Query.of(q -> q
                    .term(t -> t
                            .field("type")
                            .value(request.getType().name())
                    )
            ));
        }
    }

    private void addPriceFilters(CourseSearchRequest request, BoolQuery.Builder boolQuery) {
        if (request.getMinPrice() != null) {
            boolQuery.filter(Query.of(q -> q
                    .range(RangeQuery.of(r -> r
                            .field("price")
                            .gte(JsonData.of(request.getMinPrice()))
                    ))
            ));
        }

        if (request.getMaxPrice() != null) {
            boolQuery.filter(Query.of(q -> q
                    .range(RangeQuery.of(r -> r
                            .field("price")
                            .lte(JsonData.of(request.getMaxPrice()))
                    ))
            ));
        }
    }

    private void addDateFilter(CourseSearchRequest request, BoolQuery.Builder boolQuery) {
        if (request.getStartDate() != null) {
            boolQuery.filter(Query.of(q -> q
                    .range(RangeQuery.of(r -> r
                            .field("nextSessionDate")
                            .gte(JsonData.of(request.getStartDate().toString()))
                    ))
            ));
        }
    }

    private co.elastic.clients.elasticsearch._types.SortOptions buildSort(String sort) {
        String sortField = Optional.ofNullable(sort).orElse(DEFAULT_SORT);

        return switch (sortField) {
            case "priceAsc" -> co.elastic.clients.elasticsearch._types.SortOptions.of(s -> s
                    .field(f -> f.field("price").order(SortOrder.Asc))
            );
            case "priceDesc" -> co.elastic.clients.elasticsearch._types.SortOptions.of(s -> s
                    .field(f -> f.field("price").order(SortOrder.Desc))
            );
            default -> co.elastic.clients.elasticsearch._types.SortOptions.of(s -> s
                    .field(f -> f.field("nextSessionDate").order(SortOrder.Asc))
            );
        };
    }

    private CourseSearchResponse buildSearchResponse(SearchResponse<CourseDocument> response,
                                                     CourseSearchRequest request) {
        List<CourseDocument> courses = Optional.ofNullable(response.hits())
                .map(hits -> hits.hits())
                .orElse(Collections.emptyList())
                .stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        long total = Optional.ofNullable(response.hits())
                .map(hits -> hits.total())
                .map(totalHits -> totalHits.value())
                .orElse(0L);

        int totalPages = (int) Math.ceil((double) total / request.getSize());

        return new CourseSearchResponse(total, courses, request.getPage(), request.getSize(), totalPages);
    }
}