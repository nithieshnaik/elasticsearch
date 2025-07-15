# Course Search Application

A Spring Boot application that provides course search functionality using Elasticsearch with advanced filtering, sorting, fuzzy search, and autocomplete features.

---

## 🚀 Features

* 🔍 Full-text search with fuzzy matching on course titles and descriptions
* 🎯 Multiple filters: category, type, age range, price range, and session dates
* ↕️ Sorting options: by upcoming sessions, price (ascending/descending)
* 📄 Pagination support
* 🧠 Autocomplete suggestions for course titles
* 📦 Bulk data indexing from JSON file

---

## 🛠 Prerequisites

* Java 17 or higher
* Maven 3.6 or higher
* Docker and Docker Compose

---

## ⚙️ Setup Instructions

### 1. Start Elasticsearch

Start Elasticsearch using Docker Compose:

```bash
docker-compose up -d
```

Verify Elasticsearch is running:

```bash
curl http://localhost:9200
```

You should see a JSON response with cluster information.

### 2. Run the Spring Boot Application

```bash
mvn clean install
mvn spring-boot:run
```

The application will start at `http://localhost:8080` and will automatically:

* Connect to Elasticsearch
* Create the necessary indices
* Load sample course data from `src/main/resources/sample-courses.json`

---

## Sample Course Data Format

```json
{
  "id": "1",
  "title": "Introduction to Programming",
  "description": "Learn the fundamentals of programming with hands-on exercises and projects.",
  "category": "Technology",
  "type": "COURSE",
  "gradeRange": "6th-8th",
  "minAge": 11,
  "maxAge": 14,
  "price": 299.99,
  "nextSessionDate": "2025-08-15T10:00:00"
}
```

---

## 📦 Expected Response Format

```json
{
  "total": 2,
  "courses": [
    {
      "id": "1",
      "title": "Introduction to Programming",
      "description": "Learn the fundamentals of programming with hands-on exercises and projects.",
      "category": "Technology",
      "type": "COURSE",
      "gradeRange": "6th-8th",
      "minAge": 11,
      "maxAge": 14,
      "price": 299.99,
      "nextSessionDate": "2025-08-15T10:00:01",
      "suggest": {
        "input": [
          "Introduction to Programming",
          "introduction to programming",
          "Technology",
          "learn",
          "fundamentals",
          "programming",
          "with",
          "hands-on"
        ],
        "contexts": null,
        "weight": null
      }
    },
    {
      "id": "7",
      "title": "Robotics for Beginners",
      "description": "Introduction to robotics programming and building with hands-on projects.",
      "category": "Technology",
      "type": "COURSE",
      "gradeRange": "6th-9th",
      "minAge": 11,
      "maxAge": 15,
      "price": 349.99,
      "nextSessionDate": "2025-09-05T10:00:01",
      "suggest": {
        "input": [
          "Robotics for Beginners",
          "robotics for beginners",
          "Technology",
          "introduction",
          "robotics",
          "programming",
          "building",
          "with"
        ],
        "contexts": null,
        "weight": null
      }
    }
  ],
  "page": 0,
  "size": 10,
  "totalPages": 1
}
```

---

## 📬 cURL Examples

```bash
# Basic search
curl "http://localhost:8080/api/search?q=programming"

# With filters
curl "http://localhost:8080/api/search?category=Technology&minAge=12&maxAge=16"

# Get suggestions
curl "http://localhost:8080/api/search/suggest?q=prog"

# Debug endpoint
curl "http://localhost:8080/api/search/debug"
```

---

## 📎 Postman Documentation

You can import this [Postman Collection](https://.postman.co/workspace/My-Workspace~c6f8ee31-18ff-4f62-81ab-0a433e1295ee/collection/36707245-d73d40de-d4c8-4f6d-8a68-7f4fa1d6a17a?action=share&creator=36707245) to try out the API endpoints interactively.

---

> Developed with ❤️ using Spring Boot and Elasticsearch.
