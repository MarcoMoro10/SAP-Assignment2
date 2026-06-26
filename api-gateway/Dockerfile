FROM maven:3.9.11-eclipse-temurin-25 AS build
ENV TZ="Europe/Rome"
WORKDIR /project
COPY . /project/
RUN mvn clean package -DskipTests
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
EXPOSE 8080 9401
CMD ["java", "-jar", "target/api-gateway.jar"]
