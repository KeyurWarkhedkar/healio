# -----------------------------
# Stage 1: Build the Spring Boot app
# -----------------------------
FROM maven:3-eclipse-temurin-21-alpine AS build

# Set working directory inside the container
WORKDIR /app

# Copy only the pom.xml first to leverage Docker cache
COPY pom.xml .

# Download dependencies (cache layer)
RUN mvn dependency:go-offline -B

# Copy the rest of the source code
COPY src ./src

# Package the app (skip tests for faster builds)
RUN mvn clean package -DskipTests

# -----------------------------
# Stage 2: Run the Spring Boot app
# -----------------------------
FROM eclipse-temurin:21-jdk-alpine

# Set working directory
WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose port (match your Spring Boot server.port)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
