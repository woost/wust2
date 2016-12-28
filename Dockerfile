FROM anapsix/alpine-java

# ARG version

# COPY backend/target/scala-2.11/backend-assembly-$version.jar /app/backend.jar
COPY backend/target/scala-2.11/backend-assembly-0.1-SNAPSHOT.jar /app/backend.jar

WORKDIR /app
ENTRYPOINT java -jar backend.jar
