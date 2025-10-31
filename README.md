# Spring JWT Backend (MySQL) + FE Integration

## 1) Start MySQL (Docker)
```bash
docker compose up -d
# DB: hrdb, user: hruser/hrpass, port: 3306
```

## 2) Configure env (optional)
Spring reads these env vars with defaults:
```
DB_HOST=localhost
DB_PORT=3306
DB_NAME=hrdb
DB_USER=hruser
DB_PASS=hrpass
```

## 3) Run backend
```bash
mvn spring-boot:run
# Admin: admin / admin123
```

## 4) Dev: FE separate (CORS)
- FE: http://localhost:5173 (Vite) or :4200 (Angular)
- Set FE API base: http://localhost:8080/api
- CORS configured in `application.yml` → `app.cors.allowed-origins`

## 5) Prod: Serve FE from Spring
- Build FE → copy build output into `src/main/resources/static/`
- Rebuild:
```bash
mvn clean package
java -jar target/spring-jwt-backend-mysql-1.0.0.jar
```
Open http://localhost:8080

## Endpoints
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/employees` (with Bearer token)
- `POST /api/employees` etc.

> Change `app.jwt.secret` before production.
