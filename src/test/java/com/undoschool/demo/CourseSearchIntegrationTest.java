package com.undoschool.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.undoschool.demo.dto.CourseSearchResponse;
import com.undoschool.demo.model.CourseDocument;
import com.undoschool.demo.repository.CourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.suggest.Completion;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureWebMvc
@Testcontainers
class CourseSearchIntegrationTest {

    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.11.0")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", elasticsearch::getHttpHostAddress);
    }

    @BeforeEach
    void setUp() {
        courseRepository.deleteAll();

        // Create test data with proper Completion objects
        List<CourseDocument> testCourses = Arrays.asList(
                createCourse("1", "Python Programming", "Learn Python basics", "Technology",
                        CourseDocument.CourseType.COURSE, 12, 18, 299.99,
                        LocalDateTime.of(2025, 8, 15, 10, 0)),
                createCourse("2", "Physics 101", "Introduction to Physics", "Science",
                        CourseDocument.CourseType.COURSE, 14, 18, 199.99,
                        LocalDateTime.of(2025, 8, 20, 14, 0)),
                createCourse("3", "Art Workshop", "Creative art session", "Art",
                        CourseDocument.CourseType.ONE_TIME, 8, 16, 45.00,
                        LocalDateTime.of(2025, 7, 25, 11, 0))
        );

        courseRepository.saveAll(testCourses);

        // Wait for indexing
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private CourseDocument createCourse(String id, String title, String description,
                                        String category, CourseDocument.CourseType type,
                                        int minAge, int maxAge, double price,
                                        LocalDateTime nextSessionDate) {
        CourseDocument course = new CourseDocument();
        course.setId(id);
        course.setTitle(title);
        course.setDescription(description);
        course.setCategory(category);
        course.setType(type);
        course.setMinAge(minAge);
        course.setMaxAge(maxAge);
        course.setPrice(price);
        course.setNextSessionDate(nextSessionDate);

        // Create and set Completion object for suggestions
        Completion completion = new Completion();
        completion.setInput(new String[]{title, title.toLowerCase(), category}); // Using String array
        course.setSuggest(completion);

        return course;
    }

    @Test
    void testSearchByKeyword() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/search")
                        .param("q", "Python"))
                .andExpect(status().isOk())
                .andReturn();

        CourseSearchResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CourseSearchResponse.class
        );

        assertEquals(1, response.getTotal());
        assertEquals("Python Programming", response.getCourses().get(0).getTitle());
    }

    @Test
    void testSearchByCategory() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/search")
                        .param("category", "Science"))
                .andExpect(status().isOk())
                .andReturn();

        CourseSearchResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CourseSearchResponse.class
        );

        assertEquals(1, response.getTotal());
        assertEquals("Physics 101", response.getCourses().get(0).getTitle());
    }

    @Test
    void testSearchByPriceRange() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/search")
                        .param("minPrice", "100")
                        .param("maxPrice", "250"))
                .andExpect(status().isOk())
                .andReturn();

        CourseSearchResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CourseSearchResponse.class
        );

        assertEquals(1, response.getTotal());
        assertEquals("Physics 101", response.getCourses().get(0).getTitle());
    }

    @Test
    void testSearchByAgeRange() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/search")
                        .param("minAge", "10")
                        .param("maxAge", "12"))
                .andExpect(status().isOk())
                .andReturn();

        CourseSearchResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CourseSearchResponse.class
        );

        assertEquals(1, response.getTotal());
        assertEquals("Art Workshop", response.getCourses().get(0).getTitle());
    }

    @Test
    void testSortByPrice() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/search")
                        .param("sort", "priceAsc"))
                .andExpect(status().isOk())
                .andReturn();

        CourseSearchResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CourseSearchResponse.class
        );

        assertEquals(3, response.getTotal());
        assertEquals("Art Workshop", response.getCourses().get(0).getTitle());
        assertEquals(45.00, response.getCourses().get(0).getPrice());
    }

    @Test
    void testFuzzySearch() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/search")
                        .param("q", "Phisics"))
                .andExpect(status().isOk())
                .andReturn();

        CourseSearchResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CourseSearchResponse.class
        );

        assertEquals(1, response.getTotal());
        assertEquals("Physics 101", response.getCourses().get(0).getTitle());
    }

    @Test
    void testAutocompleteSuggestions() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/search/suggest")
                        .param("q", "Py"))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        List<String> suggestions = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                List.class
        );

        assertFalse(suggestions.isEmpty());
        assertTrue(suggestions.contains("Python Programming"));
    }
}