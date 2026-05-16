# Palmistry Lab Project

This workspace contains a simple frontend prototype set and a Spring Boot backend, plus docs and sample data.

## Structure

- backend/          Spring Boot service (source, resources, target)
- frontend/         Static HTML entry points
- frontend/variants/Additional UI variants and concept screens
- docs/             Project docs and reports
- data/             Sample JSON responses

## Notes

- Frontend entry points: frontend/index.html, frontend/admin_metrics.html
- Backend entry: backend/src/main/java/.../PalmistryBackendApplication.java
- Crash logs (if any): backend/logs/

## Run

### Backend (Spring Boot)

Prerequisites: Java 17+, Maven 3.9+.

If you hit low-memory errors on Windows, try a smaller heap:

```bash
# PowerShell
$env:MAVEN_OPTS="-Xmx512m"; mvn spring-boot:run
```

```bash
cd backend
mvn spring-boot:run
```

Service: http://localhost:8080

UI entry (served by backend static resources): http://localhost:8080/

Optional profiles:

```bash
# dev (H2)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# prod (MySQL)
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### Frontend (static HTML)

Open these files directly in a browser after the backend is running:

- frontend/index.html
- frontend/admin_metrics.html

UI variants are under frontend/variants/.

Static assets served by Spring Boot live in backend/src/main/resources/static/.
