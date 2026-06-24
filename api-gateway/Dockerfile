FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /project
COPY . /project/
RUN mvn clean package -DskipTests
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
EXPOSE 8080
CMD ["mvn", "exec:java", "-Dexec.mainClass=it.unibo.sap.gateway.infrastructure.APIGatewayMain"]
