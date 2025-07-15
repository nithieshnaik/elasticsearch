package com.undoschool.demo.controller;


import com.undoschool.demo.dto.CourseSearchRequest;
import com.undoschool.demo.dto.CourseSearchResponse;
import com.undoschool.demo.model.CourseDocument;
import com.undoschool.demo.service.CourseSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class CourseSearchController {

    @Autowired
    private CourseSearchService courseSearchService;

    @GetMapping
    public ResponseEntity<CourseSearchResponse> searchCourses(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer minAge,
            @RequestParam(required = false) Integer maxAge,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String startDate,
            @RequestParam(defaultValue = "upcoming") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        CourseSearchRequest request = new CourseSearchRequest();
        request.setQ(q);
        request.setMinAge(minAge);
        request.setMaxAge(maxAge);
        request.setCategory(category);

        if (type != null) {
            try {
                request.setType(CourseDocument.CourseType.valueOf(type.toUpperCase()));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        }

        request.setMinPrice(minPrice);
        request.setMaxPrice(maxPrice);

        if (startDate != null) {
            try {
                request.setStartDate(java.time.LocalDateTime.parse(startDate));
            } catch (Exception e) {
                return ResponseEntity.badRequest().build();
            }
        }

        request.setSort(sort);
        request.setPage(page);
        request.setSize(size);

        CourseSearchResponse response = courseSearchService.searchCourses(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/suggest")
    public ResponseEntity<List<String>> getSuggestions(@RequestParam String q) {
        List<String> suggestions = courseSearchService.getSuggestions(q);
        return ResponseEntity.ok(suggestions);
    }

   @GetMapping("/debug")
    public ResponseEntity<Map<String, Object>> debugSearch() {
        Map<String, Object> debug = new HashMap<>();

        try {
            long totalCount = courseSearchService.getTotalCourseCount();
            List<CourseDocument> allCourses = courseSearchService.getAllCourses();

            debug.put("totalCount", totalCount);
            debug.put("courses", allCourses);
            debug.put("indexExists", totalCount > 0);

            return ResponseEntity.ok(debug);
        } catch (Exception e) {
            debug.put("error", e.getMessage());
            return ResponseEntity.status(500).body(debug);
        }
    }

    // Add this endpoint to test specific queries
    @GetMapping("/test")
    public ResponseEntity<Object> testQuery(@RequestParam String query) {
        try {
            CourseSearchRequest request = new CourseSearchRequest();
            request.setQ(query);
            request.setPage(0);
            request.setSize(10);

            CourseSearchResponse response = courseSearchService.searchCourses(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}


