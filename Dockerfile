FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY lib ./lib
# Install the local KuaiShou SDK JAR into the Maven local repository
RUN mvn install:install-file \
    -Dfile=lib/kuaishou-merchant-open-sdk-release_open_kwaishop_sdk-1.0.7633.jar \
    -DgroupId=com.kuaishou \
    -DartifactId=merchant-open-sdk \
    -Dversion=1.0.7633 \
    -Dpackaging=jar \
    -DgeneratePom=true
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/kwai_data-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
