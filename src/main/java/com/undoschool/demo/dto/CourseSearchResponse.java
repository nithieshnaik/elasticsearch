package com.undoschool.demo.dto;

import com.undoschool.demo.model.CourseDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseSearchResponse {

    private long total;
    private List<CourseDocument> courses;
    private int page;
    private int size;
    private int totalPages;
}