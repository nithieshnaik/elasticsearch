package com.undoschool.demo.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.undoschool.demo.model.CourseDocument;
import com.undoschool.demo.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.elasticsearch.BulkFailureException;
import org.springframework.data.elasticsearch.core.suggest.Completion;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataLoader implements CommandLineRunner {

    private final CourseRepository courseRepository;
    private final ObjectMapper objectMapper;
    private final ElasticsearchClient elasticsearchClient;

    @Value("${app.elasticsearch.index.courses:courses}")
    private String coursesIndex;

    @Value("${app.data.sample-file:course.json}")
    private String sampleDataFile;

    @Value("${app.data.force-reload:false}")
    private boolean forceReload;

    private static final DateTimeFormatter FLEXIBLE_FORMATTER = DateTimeFormatter.ofPattern(
            "[yyyy-MM-dd'T'HH:mm:ss[.SSS][XXX]]" +
                    "[yyyy-MM-dd'T'HH:mm:ss[.SSS]]" +
                    "[yyyy-MM-dd'T'HH:mm]"
    );

    // Formatter to ensure seconds are always included for Elasticsearch
    private static final DateTimeFormatter ES_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public void run(String... args) {
        try {
            log.info("Starting data loading process...");

            if (!testElasticsearchConnectionWithRetry()) {
                log.error("Elasticsearch connection failed. Skipping data loading.");
                return;
            }

            ensureIndexWithProperMapping();

            long count = courseRepository.count();
            log.info("Current course count: {}", count);

            if (count == 0 || forceReload) {
                if (forceReload && count > 0) {
                    log.info("Force reload enabled, clearing existing data...");
                    courseRepository.deleteAll();
                    Thread.sleep(1000);
                }
                loadSampleData();
            } else {
                log.info("Course data already exists, skipping data loading");
            }

            log.info("Data loading process completed successfully");
        } catch (Exception e) {
            log.error("Error during data loading initialization", e);
        }
    }

    private void ensureIndexWithProperMapping() throws Exception {
        boolean indexExists = elasticsearchClient.indices()
                .exists(e -> e.index(coursesIndex))
                .value();

        if (!indexExists) {
            elasticsearchClient.indices().create(c -> c
                    .index(coursesIndex)
                    .mappings(m -> m
                            .properties("title", p -> p
                                    .text(t -> t
                                            .analyzer("english")
                                            .fields("keyword", f -> f.keyword(k -> k))
                                    )
                            )
                            .properties("description", p -> p
                                    .text(t -> t.analyzer("english"))
                            )
                            .properties("category", p -> p.keyword(k -> k))
                            .properties("type", p -> p.keyword(k -> k))
                            .properties("nextSessionDate", p -> p
                                    .date(d -> d.format("yyyy-MM-dd'T'HH:mm:ss"))
                            )
                            .properties("suggest", p -> p.completion(cp -> cp))
                    )
            );
            log.info("Created index with proper mapping");
        }
    }
    private boolean testElasticsearchConnectionWithRetry() {
        int maxRetries = 3;
        int retryDelaySeconds = 5;

        for (int i = 0; i < maxRetries; i++) {
            try {
                if (elasticsearchClient == null) {
                    log.error("ElasticsearchClient is null - check configuration");
                    return false;
                }

                elasticsearchClient.info();
                log.info("Elasticsearch connection successful");
                return true;

            } catch (Exception e) {
                log.warn("Elasticsearch connection attempt {} failed: {}", i + 1, e.getMessage());

                if (i < maxRetries - 1) {
                    try {
                        log.info("Retrying in {} seconds...", retryDelaySeconds);
                        TimeUnit.SECONDS.sleep(retryDelaySeconds);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Thread interrupted while waiting for retry", ie);
                        return false;
                    }
                }
            }
        }

        log.error("Failed to connect to Elasticsearch after {} attempts", maxRetries);
        return false;
    }

    private void loadSampleData() {
        try {
            log.info("Loading course data from: {}", sampleDataFile);

            // Read raw JSON data
            ClassPathResource resource = new ClassPathResource(sampleDataFile);
            List<CourseDocument> courses = objectMapper.readValue(
                    resource.getInputStream(),
                    new TypeReference<List<CourseDocument>>() {}
            );

            // Process and normalize dates
            courses.forEach(course -> {
                if (course.getNextSessionDate() != null) {
                    try {
                        String dateStr = course.getNextSessionDate().toString();
                        LocalDateTime normalizedDate = parseFlexibleDateTime(dateStr);
                        course.setNextSessionDate(normalizedDate);

                        // Log the final formatted date that will be sent to Elasticsearch
                        String formattedForEs = normalizedDate.format(ES_FORMATTER);
                        log.debug("Parsed date for course {}: {} -> {} (ES format: {})",
                                course.getId(), dateStr, normalizedDate, formattedForEs);

                        addCompletionSuggestions(course);

                        if (!validateCourse(course)) {
                            log.warn("Skipping invalid course: {}", course.getId());
                            return;
                        }
                    } catch (Exception e) {
                        log.warn("Invalid date format for course {}: {} - {}",
                                course.getId(), course.getNextSessionDate(), e.getMessage());
                        course.setNextSessionDate(null);
                    }
                }
            });

            // Filter out invalid courses
            courses = courses.stream()
                    .filter(course -> course.getNextSessionDate() != null)
                    .collect(Collectors.toList());

            log.info("Processed {} valid courses", courses.size());

            // Save in batches
            final int batchSize = 50;
            for (int i = 0; i < courses.size(); i += batchSize) {
                List<CourseDocument> batch = courses.subList(
                        i, Math.min(i + batchSize, courses.size())
                );

                try {
                    courseRepository.saveAll(batch);
                    log.info("Successfully processed batch {} to {} ({} courses)",
                            i, Math.min(i + batchSize, courses.size()), batch.size());
                } catch (BulkFailureException e) {
                    log.error("Partial batch failure at {} to {}: {}",
                            i, Math.min(i + batchSize, courses.size()), e.getMessage());

                    // Retry individual documents
                    batch.forEach(doc -> {
                        try {
                            courseRepository.save(doc);
                            log.debug("Successfully saved document: {}", doc.getId());
                        } catch (Exception ex) {
                            log.error("Failed to save document {}: {}", doc.getId(), ex.getMessage());
                        }
                    });
                }
            }

            log.info("Completed loading {} courses", courses.size());
        } catch (Exception e) {
            log.error("Failed to load course data", e);
            throw new RuntimeException("Data loading failed", e);
        }
    }

    private LocalDateTime parseFlexibleDateTime(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            throw new IllegalArgumentException("Date string cannot be null or empty");
        }

        log.debug("Parsing date string: {}", dateStr);

        // Remove timezone indicator if present
        if (dateStr.endsWith("Z")) {
            dateStr = dateStr.substring(0, dateStr.length() - 1);
        }

        // Handle different date formats - FIXED: Don't remove seconds, add them if missing
        if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}$")) {
            // Add seconds if missing: 2025-08-20T00:00 -> 2025-08-20T00:00:01
            // Adding :01 instead of :00 to prevent LocalDateTime.toString() from removing seconds
            dateStr = dateStr + ":01";
            log.debug("Added seconds to date: {}", dateStr);
        } else if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}$")) {
            // Remove milliseconds: 2025-08-20T00:00:00.000 -> 2025-08-20T00:00:00
            dateStr = dateStr.substring(0, dateStr.lastIndexOf('.'));
            log.debug("Removed milliseconds from date: {}", dateStr);
        } else if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:00$")) {
            // Change :00 seconds to :01 to prevent toString() from removing them
            dateStr = dateStr.substring(0, dateStr.length() - 2) + "01";
            log.debug("Changed :00 seconds to :01 in date: {}", dateStr);
        }
        // If it already has non-zero seconds, keep it as is

        try {
            LocalDateTime result = LocalDateTime.parse(dateStr, FLEXIBLE_FORMATTER);
            log.debug("Successfully parsed date: {} -> {}", dateStr, result);
            return result;
        } catch (Exception e) {
            log.error("Could not parse date string: {}", dateStr);
            throw new IllegalArgumentException("Invalid date format: " + dateStr, e);
        }
    }

    private boolean validateCourse(CourseDocument course) {
        if (course == null) {
            log.warn("Null course found, skipping");
            return false;
        }

        if (!StringUtils.hasText(course.getTitle())) {
            log.warn("Course with empty title found, skipping: {}", course.getId());
            return false;
        }

        if (!StringUtils.hasText(course.getDescription())) {
            log.warn("Course with empty description found: {}", course.getTitle());
        }

        if (course.getMinAge() != null && course.getMaxAge() != null &&
                course.getMinAge() > course.getMaxAge()) {
            log.warn("Course with invalid age range found: {} (min: {}, max: {})",
                    course.getTitle(), course.getMinAge(), course.getMaxAge());
            int temp = course.getMinAge();
            course.setMinAge(course.getMaxAge());
            course.setMaxAge(temp);
        }

        if (course.getPrice() != null && course.getPrice() < 0) {
            log.warn("Course with negative price found: {} (price: {})",
                    course.getTitle(), course.getPrice());
            course.setPrice(0.0);
        }

        return true;
    }

    private void addCompletionSuggestions(CourseDocument course) {
        try {
            if (StringUtils.hasText(course.getTitle())) {
                Completion completion = new Completion();
                List<String> inputs = new ArrayList<>();

                inputs.add(course.getTitle());
                inputs.add(course.getTitle().toLowerCase());

                if (course.getCategory() != null) {
                    inputs.add(course.getCategory());
                }

                if (StringUtils.hasText(course.getDescription())) {
                    Arrays.stream(course.getDescription().toLowerCase().split("\\s+"))
                            .filter(word -> word.length() > 3)
                            .filter(word -> !Set.of("comprehensive", "class", "designed", "for", "and", "the").contains(word))
                            .limit(5)
                            .forEach(inputs::add);
                }

                completion.setInput(inputs.toArray(new String[0]));
                course.setSuggest(completion);
            }
        } catch (Exception e) {
            log.warn("Failed to set completion suggestions for course: {}", course.getTitle(), e);
        }
    }
}