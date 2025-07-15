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

## 📬 Test Endpoints

### Base URL

```
http://localhost:8080
```

### 🔧 Debug

* `GET /api/search/debug` — Check if data is loaded
* `GET /api/search/test?query=programming` — Basic test query

### 🔍 Search

* `GET /api/search` — Get all courses
* `GET /api/search?q=programming` — Search by keyword
* `GET /api/search?q=prog` — Partial match

### 📂 Filter Examples

* `GET /api/search?category=Technology`
* `GET /api/search?type=COURSE`
* `GET /api/search?minAge=10&maxAge=14`
* `GET /api/search?minPrice=100&maxPrice=300`
* `GET /api/search?startDate=2025-08-15T10:00:00`

### 🔀 Combined Filters

* `GET /api/search?q=programming&category=Technology&minAge=11&maxAge=16`
* `GET /api/search?category=Science&type=CLUB&maxPrice=200`

### 📄 Pagination

* `GET /api/search?page=0&size=5`

### 📊 Sorting

* `GET /api/search?sort=upcoming`
* `GET /api/search?sort=price_asc`

### 💡 Suggestions

* `GET /api/search/suggest?q=prog`
* `GET /api/search/suggest?q=math`

### ❗ Edge Cases

* `GET /api/search?q=` — Empty query
* `GET /api/search?q=C++` — Special characters
* `GET /api/search?type=INVALID_TYPE` — Invalid type

---

## 📎 Postman Documentation

You can import this [Postman Collection](https://www.postman.com/) to try out the API endpoints interactively.

---

## 📌 cURL Examples

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

> Developed with ❤️ using Spring Boot and Elasticsearch.
