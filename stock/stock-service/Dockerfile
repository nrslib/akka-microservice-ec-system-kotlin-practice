FROM openjdk:8-jdk-alpine
VOLUME /tmp
RUN mkdir /work
RUN mkdir /work/build
RUN mkdir /work/build/libs

ENV APP_VERSION=1.0-SNAPSHOT

COPY ./build/libs/stock-service-$APP_VERSION-all.jar /work/build/libs
WORKDIR /work
RUN mv /work/build/libs/*.jar /work/app.jar

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/work/app.jar"]
