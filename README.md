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

### API Endpoints

- **Health Check:** `GET /api/v1.0/health`
- **Login:** `POST /api/v1.0/login`
- **Categories:** `GET /api/v1.0/categories`
- **Items:** `GET /api/v1.0/items`
- **Orders:** `POST /api/v1.0/orders`

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

This project is proprietary software.
