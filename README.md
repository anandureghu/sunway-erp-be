# Sunway ERP — Backend (Spring Boot)

[Deploy to Azure Container Apps](https://github.com/anandureghu/sunway-erp-be/actions/workflows/deploy.yml)

Spring Boot 3 API with JWT auth and MySQL. Runs on **port 8080** by default; API routes are under `/api`.

## Prerequisites

- **JDK 21**
- **Maven 3.8+** (`mvn` on your PATH)
- **MySQL 8** — easiest option is Docker using the compose file in this folder

## 1. Start MySQL (Docker)

From the `backend` directory:

```bash
docker compose up -d
```

This starts MySQL on **port 3306** with:

| Setting  | Value    |
| -------- | -------- |
| Database | `hrdb`   |
| User     | `hruser` |
| Password | `hrpass` |

## 2. Database environment variables

`src/main/resources/application-mysql.yml` defaults do **not** match the Docker Compose user above. When using the compose MySQL, set:

```bash
export DB_HOST=localhost DB_PORT=3306 DB_NAME=hrdb DB_USER=hruser DB_PASS=hrpass
```

Alternatively, create a `backend/.env` file (Spring loads `optional:file:.env`):

```properties
DB_HOST=localhost
DB_PORT=3306
DB_NAME=hrdb
DB_USER=hruser
DB_PASS=hrpass
```

If you use your own MySQL instance, set these variables to match your server.

## 3. Optional configuration

| Variable                          | Purpose                                                                           |
| --------------------------------- | --------------------------------------------------------------------------------- |
| `AZURE_STORAGE_CONNECTION_STRING` | Azure Blob storage (file uploads). Omit locally if you do not use those features. |
| `APP_PUBLIC_BASE_URL`             | Public frontend URL for emails, PDFs, and assistant links (default `http://localhost:5173`). Set to e.g. `https://demo.sunwayerp.com` in demo. |

Change `app.jwt.secret` in `src/main/resources/application.yml` before any production deployment.

## 4. Run the application

From the `backend` directory:

```bash
mvn spring-boot:run
```

- **API base:** [http://localhost:8080/api](http://localhost:8080/api)
- **Swagger UI:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

A seeded admin user is created on startup (see `AdminSeeder`): `admin` **/** `admin123`.

## 5. Build a runnable JAR

```bash
mvn clean package
java -jar target/sunway-1.0.0.jar
```

## Frontend (local dev)

The React app in `sunway-erp-web` usually runs on [http://localhost:5173](http://localhost:5173). CORS allowed origins include that URL — see `app.cors.allowed-origins` in `src/main/resources/application.yml`. For full UI setup, see `../sunway-erp-web/README.md`.

## Useful endpoints

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- Authenticated routes under `/api/...` with a `Bearer` access token
