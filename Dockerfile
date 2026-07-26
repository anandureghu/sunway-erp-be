# ============================
# Stage 1: Build with Maven + Java 21
# ============================
FROM eclipse-temurin:21-jdk AS builder

RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package spring-boot:repackage -DskipTests

# ============================
# Stage 2: Run with lightweight Java 21 image
# ============================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
