# Build stage (using Java 21)
FROM eclipse-temurin:21-jdk-jammy as builder
WORKDIR /workspace/app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src

RUN ./mvnw clean package -DskipTests

# Run stage (also using Java 21)
FROM eclipse-temurin:21-jre-jammy
LABEL authors="Nithiesh Naik"
WORKDIR /app

COPY --from=builder /workspace/app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]