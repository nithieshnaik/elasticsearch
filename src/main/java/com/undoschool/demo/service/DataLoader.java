package com.undoschool.demo.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.undoschool.demo.model.CourseDocument;
import com.undoschool.demo.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.elasticsearch.core.suggest.Completion;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataLoader implements CommandLineRunner {

    private  CourseRepository courseRepository;
    private  ObjectMapper objectMapper;
    private  ElasticsearchClient elasticsearchClient;

    @Autowired
    public DataLoader(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }


    @Override
    public void run(String... args) {
        try {
            // Test Elasticsearch connection
            if (!testElasticsearchConnection()) {
                log.error("Elasticsearch connection failed. Skipping data loading.");
                return;
            }

            log.info("Checking if sample data exists...");

            long count = courseRepository.count();
            log.info("Current course count: {}", count);

            if (count == 0) {
                loadSampleData();
            } else {
                log.info("Sample data already exists, skipping data loading");
            }
        } catch (Exception e) {
            log.error("Error during data loading initialization", e);
        }
    }

    private boolean testElasticsearchConnection() {
        try {
            elasticsearchClient.info();
            log.info("Elasticsearch connection successful");
            return true;
        } catch (Exception e) {
            log.error("Failed to connect to Elasticsearch", e);
            return false;
        }
    }

    private void loadSampleData() {
        try {
            log.info("Loading sample course data...");

            ClassPathResource resource = new ClassPathResource("sample-courses.json");

            if (!resource.exists()) {
                log.warn("Sample data file 'sample-courses.json' not found in classpath");
                createDefaultSampleData();
                return;
            }

            InputStream inputStream = resource.getInputStream();
            List<CourseDocument> courses = objectMapper.readValue(inputStream, new TypeReference<List<CourseDocument>>() {});

            // Add completion suggestions for each course
            courses.forEach(this::addCompletionSuggestions);

            courseRepository.saveAll(courses);
            log.info("Successfully loaded {} courses", courses.size());

        } catch (Exception e) {
            log.error("Failed to load sample data", e);
            createDefaultSampleData();
        }
    }

    private void addCompletionSuggestions(CourseDocument course) {
        try {
            if (course.getTitle() != null && !course.getTitle().isEmpty()) {
                Completion completion = new Completion();
                completion.setInput(new String[]{
                        course.getTitle(),
                        course.getTitle().toLowerCase(),
                        course.getCategory() != null ? course.getCategory() : "general"
                });
                course.setSuggest(completion);
            }
        } catch (Exception e) {
            log.warn("Failed to set completion for course: {}", course.getTitle(), e);
        }
    }

    private void createDefaultSampleData() {
        try {
            log.info("Creating default sample data...");

            CourseDocument course1 = new CourseDocument();
            course1.setId("1");
            course1.setTitle("Introduction to Programming");
            course1.setDescription("Learn the basics of programming");
            course1.setCategory("Technology");
            course1.setType(CourseDocument.CourseType.COURSE);
            course1.setGradeRange("6-12");
            course1.setMinAge(10);
            course1.setMaxAge(18);
            course1.setPrice(99.99);

            addCompletionSuggestions(course1);

            courseRepository.save(course1);
            log.info("Created default sample course");

        } catch (Exception e) {
            log.error("Failed to create default sample data", e);
        }
    }
}