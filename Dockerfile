# ============================
# Stage 1: Build with Maven + Java 23
# ============================
FROM eclipse-temurin:23-jdk AS builder

# Install Maven manually
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy pom.xml and preload dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build JAR
COPY src ./src
RUN mvn clean package spring-boot:repackage -DskipTests

# ============================
# Stage 2: Run with lightweight Java 23 image
# ============================
FROM eclipse-temurin:23-jdk-alpine

WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose app port
EXPOSE 8080

# Start Spring Boot app
ENTRYPOINT ["java", "-jar", "app.jar"]
