FROM maven:3.9.11-eclipse-temurin-25 AS build
ENV TZ="Europe/Rome"
WORKDIR /project
COPY . /project/
RUN mvn clean package -DskipTests
EXPOSE 9002 9003 9400
CMD ["java", "-jar", "target/delivery-service.jar"]
