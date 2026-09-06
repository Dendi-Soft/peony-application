FROM gradle:jdk25-noble
LABEL authors="cristi"

WORKDIR /app
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x ./gradlew
RUN ./gradlew bootJar
RUN cp ./build/libs/*.jar .
RUN mv ./*.jar ./app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]