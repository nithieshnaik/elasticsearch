package com.undoschool.demo.dto;


import com.undoschool.demo.model.CourseDocument;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class CourseSearchRequest {

    private String q;
    private Integer minAge;
    private Integer maxAge;
    private String category;
    private CourseDocument.CourseType type;
    private Double minPrice;
    private Double maxPrice;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startDate;

    private String sort = "upcoming";
    private int page = 0;
    private int size = 10;
}