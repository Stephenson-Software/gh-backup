# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Skip tests during Docker build to speed up image creation
# Tests should be run in CI/CD pipeline before building the image
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install git (required for cloning repositories)
RUN apk add --no-cache git

# Copy the built jar from build stage
COPY --from=build /app/target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar gh-backup.jar

# Create backup directory
RUN mkdir -p /backups

# Set default environment variables
ENV BACKUP_DIRECTORY=/backups
ENV SCHEDULED_USERS=""
ENV BACKUP_INTERVAL_MS=""

# Expose port 8080 for optional web mode.
# The default image entrypoint runs in daemon mode, so this port is only used if the
# container is started in web mode by overriding the default startup behavior.
EXPOSE 8080

# Create entrypoint script
COPY docker-entrypoint.sh /app/
RUN chmod +x /app/docker-entrypoint.sh

# Default to daemon mode with scheduled backups
ENTRYPOINT ["/app/docker-entrypoint.sh"]
