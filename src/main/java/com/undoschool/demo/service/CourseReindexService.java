package com.undoschool.demo.service;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import com.undoschool.demo.model.CourseDocument;
import com.undoschool.demo.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CourseReindexService {

    private final ElasticsearchClient elasticsearchClient;
    private final CourseRepository courseRepository;


    @Value("${app.elasticsearch.index.courses}")
    private String coursesIndex;

    public void reindexAllCourses() {
        try {
            // delete index if it exists
            try {
                elasticsearchClient.indices().delete(d -> d.index(coursesIndex));
            } catch (Exception e) {
                log.warn("Index {} did not exist or could not be deleted", coursesIndex);
            }

            // fetch all courses
            List<CourseDocument> courses = (List<CourseDocument>) courseRepository.findAll();

            // bulk index
            BulkRequest.Builder bulk = new BulkRequest.Builder();
            for (CourseDocument course : courses) {
                bulk.operations(op -> op
                        .index(idx -> idx
                                .index(coursesIndex)
                                .id(course.getId())
                                .document(course)));
            }
            elasticsearchClient.bulk(bulk.build());

            log.info("Successfully reindexed {} courses", courses.size());
        } catch (Exception e) {
            log.error("Failed to reindex courses", e);
            throw new RuntimeException("Reindexing failed", e);
        }
    }
}
