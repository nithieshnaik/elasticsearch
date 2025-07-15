package com.undoschool.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

@SpringBootTest
class UndoSchoolApplicationTests {

	@Container
	static ElasticsearchContainer elasticsearchContainer =
			new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.11.0")
					.withPassword("password");

	@DynamicPropertySource
	static void elasticsearchProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.elasticsearch.uris", elasticsearchContainer::getHttpHostAddress);
	}

	@Test
	void contextLoads() {
		// Test that the application context loads successfully
	}

	@Test
	void mainMethodRuns() {
		UndoSchoolApplication.main(new String[] {});
	}


}