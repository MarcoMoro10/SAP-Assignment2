FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /project
COPY . /project/
RUN mvn clean package -DskipTests
EXPOSE 9000
CMD ["mvn", "exec:java", "-Dexec.mainClass=it.unibo.sap.account.infrastructure.AccountServiceMain"]
