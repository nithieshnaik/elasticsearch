package com.undoschool.demo.controller;


import com.undoschool.demo.dto.CourseSearchRequest;
import com.undoschool.demo.dto.CourseSearchResponse;
import com.undoschool.demo.model.CourseDocument;
import com.undoschool.demo.service.CourseSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class CourseSearchController {

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
}


