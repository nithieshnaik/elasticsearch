package com.undoschool.demo.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggestOption;
import co.elastic.clients.json.JsonData;
import com.undoschool.demo.dto.CourseSearchRequest;
import com.undoschool.demo.dto.CourseSearchResponse;
import com.undoschool.demo.model.CourseDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
            log.info("Searching with request: {}", request);
            SearchRequest searchRequest = buildSearchRequest(request);
            log.debug("Final Elasticsearch query: {}", searchRequest.toString());

            SearchResponse<CourseDocument> response = elasticsearchClient.search(searchRequest, CourseDocument.class);
            log.info("Search response - Total hits: {}",
                    response.hits().total() != null ? response.hits().total().value() : "null");

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
        return SearchRequest.of(s -> s
                .index(coursesIndex)
                .query(buildQuery(request))
                .sort(buildSort(request.getSort()))
                .from(request.getPage() * request.getSize())
                .size(request.getSize())
                .highlight(h -> h
                        .fields("title", f -> f.preTags("<em>").postTags("</em>"))
                        .fields("description", f -> f.preTags("<em>").postTags("</em>"))
                )
        );
    }

    private Query buildQuery(CourseSearchRequest request) {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        // Search query handling
        if (StringUtils.hasText(request.getQ())) {
            String query = request.getQ().trim().toLowerCase();
            log.info("Building search query for: '{}'", query);

            // Exact match with high boost
            boolQuery.should(Query.of(q -> q
                    .match(m -> m
                            .field("title")
                            .query(query)
                            .boost(3.0f)
                    )
            ));

            // Edge ngram for partial matches
            boolQuery.should(Query.of(q -> q
                    .match(m -> m
                            .field("title.edge_ngram")
                            .query(query)
                            .boost(2.0f)
                    )
            ));

            // Standard multi-match with fuzziness
            boolQuery.should(Query.of(q -> q
                    .multiMatch(m -> m
                            .query(query)
                            .fields("title^3", "description^2", "category")
                            .type(TextQueryType.BestFields)
                            .fuzziness("AUTO")
                            .operator(Operator.Or)
                    )
            ));

            // Wildcard as fallback
            boolQuery.should(Query.of(q -> q
                    .wildcard(w -> w
                            .field("title")
                            .value("*" + query + "*")
                            .boost(0.5f)
                    )
            ));

            boolQuery.minimumShouldMatch("1");
        } else {
            boolQuery.must(Query.of(q -> q.matchAll(MatchAllQuery.of(m -> m))));
        }

        // Add filters
        addAgeFilters(request, boolQuery);
        addCategoryFilter(request, boolQuery);
        addTypeFilter(request, boolQuery);
        addPriceFilters(request, boolQuery);
        addDateFilter(request, boolQuery);

        return boolQuery.build()._toQuery();
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

    public long getTotalCourseCount() {
        try {
            SearchRequest countRequest = SearchRequest.of(s -> s
                    .index(coursesIndex)
                    .query(Query.of(q -> q.matchAll(MatchAllQuery.of(m -> m))))
                    .size(0)
            );

            SearchResponse<CourseDocument> response = elasticsearchClient.search(countRequest, CourseDocument.class);
            long total = Optional.ofNullable(response.hits())
                    .map(hits -> hits.total())
                    .map(totalHits -> totalHits.value())
                    .orElse(0L);

            log.info("Total courses in index '{}': {}", coursesIndex, total);
            return total;
        } catch (Exception e) {
            log.error("Error counting courses", e);
            return 0;
        }
    }

    public List<CourseDocument> getAllCourses() {
        try {
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(coursesIndex)
                    .query(Query.of(q -> q.matchAll(MatchAllQuery.of(m -> m))))
                    .size(100)
            );

            SearchResponse<CourseDocument> response = elasticsearchClient.search(searchRequest, CourseDocument.class);
            List<CourseDocument> courses = Optional.ofNullable(response.hits())
                    .map(hits -> hits.hits())
                    .orElse(Collections.emptyList())
                    .stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.info("Retrieved {} courses from index", courses.size());
            return courses;
        } catch (Exception e) {
            log.error("Error getting all courses", e);
            return Collections.emptyList();
        }
    }
}