# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/

RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd --system spring \
    && useradd --system --gid spring --create-home --home-dir /home/spring spring

ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS=""

COPY --from=builder --chown=spring:spring /build/target/*.jar /app/app.jar

EXPOSE 8123

USER spring

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Duser.timezone=${TZ} -jar /app/app.jar"]
