# Step 1: Build the application using Maven
FROM maven:3.8.4-openjdk-17 AS build

# Set the working directory inside the container
WORKDIR /app

# Copy the pom.xml and src folder to the container
COPY ./pom.xml ./
COPY ./src ./src

# Build the application
RUN mvn clean package

# Step 2: Create a smaller image for running the application
FROM openjdk:17-jdk-slim

# Copy the JAR file from the build stage
COPY --from=build /app/target/KarNor-0.0.1-SNAPSHOT.jar /app.jar

# Expose the port on which the application will run
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "/app.jar"]
