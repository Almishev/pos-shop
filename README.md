# Supermarket POS - Backend

Spring Boot backend application for Supermarket POS system.

## 🚀 Quick Start with Docker

### Prerequisites
- Docker and Docker Compose installed
- PostgreSQL database

### Running with Docker

1. **Build and run:**
```bash
docker build -t supermarket-pos-backend .
docker run -p 8087:8087 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/billing_app \
  -e SPRING_DATASOURCE_USERNAME=user1 \
  -e SPRING_DATASOURCE_PASSWORD=asroma \
  supermarket-pos-backend
```

2. **Or use with docker-compose:**
```bash
# From the main project directory
docker-compose up backend
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/billing_app` | Database URL |
| `SPRING_DATASOURCE_USERNAME` | `user1` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `asroma` | Database password |
| `SERVER_PORT` | `8087` | Application port |
| `JWT_SECRET_KEY` | `thisismysecretkeyfortheupcomingproject` | JWT secret |
| `BACKUP_LOCAL_DIR` | `E:/shop-backups` (local) / `/app/archives/db-backups` (Docker) | Local dump directory |
| `BACKUP_RETENTION_DAYS` | `30` | Delete local backups older than N days |
| `BACKUP_SCHEDULE_ENABLED` | `true` | Nightly backup at 03:00 + startup catch-up |
| `BACKUP_HOST_PATH` | (compose only) | Host path for USB/external disk mount |
| `BACKUP_ENV_FILE` | (empty) / `/app/backup-include/.env` in Docker | Shop `.env` copied to backup dir as `pos-client.env` |

### API Endpoints

- **Health Check:** `GET /api/v1.0/health`
- **Login:** `POST /api/v1.0/login`
- **Categories:** `GET /api/v1.0/categories`
- **Items:** `GET /api/v1.0/items`
- **Orders:** `POST /api/v1.0/orders`
- **DB backup (ADMIN):** `POST /api/v1.0/admin/backup?destination=local\|s3`
- **List backups (ADMIN):** `GET /api/v1.0/admin/backup`
- **Download backup (ADMIN):** `GET /api/v1.0/admin/backup/{filename}`

## 💾 Database backup

Creates a full `pg_dump` → `.sql.gz`. Does **not** delete PostgreSQL data. Requires `pg_dump` on the host PATH (local) or `postgresql-client` in the Docker image.

- UI: **Reports → Database backup** (local / AWS)
- Schedule: every day at **03:00**; catch-up on startup if the nightly run was missed
- Retention: **30 days**
- Docker: set `BACKUP_HOST_PATH` to the USB/external disk folder; compose mounts it into the container
- Restore: stop the app → empty `billing_app` → `psql -f backup.sql` (or gunzip pipe) → start the app. Never start Spring on an empty DB before restore (`ddl-auto` would create empty tables and break the dump).

See also [CLIENT/README.CLIENT.md](../CLIENT/README.CLIENT.md) (operator restore steps).

### Development

```bash
# Run locally
./mvnw spring-boot:run

# Run tests
./mvnw test

# Build JAR
./mvnw clean package
```

## 📦 Docker Image

The Docker image is available on Docker Hub:
```bash
docker pull antonalmishev/supermarket-pos-backend:latest
```

## 🔧 Configuration

The application uses Spring Boot configuration with the following profiles:
- `application.properties` - Main configuration
- `application-docker.properties` - Docker-specific settings

## 📝 License

This project is Anton Almishev software.
