FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle settings.gradle gradle.properties ./
COPY gradle gradle
COPY src src

RUN chmod +x gradlew && ./gradlew --no-daemon clean vaadinBuildFrontend bootJar -Pvaadin.productionMode=true

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
