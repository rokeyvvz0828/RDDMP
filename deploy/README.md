# Docker test deployment

The test deployment runs the Vue gateway, Spring Boot application, MySQL, MinIO, and kkFileView on one x86_64 host.

## Build application images

```bash
TAG=$(git rev-parse --short HEAD)
docker buildx build --platform linux/amd64 --load -f deploy/backend.Dockerfile -t "rddmp/backend:${TAG}" .
docker buildx build --platform linux/amd64 --load -f deploy/frontend.Dockerfile -t "rddmp/web:${TAG}" .
```

## Start services

Create `runtime.env` from `runtime.env.example`, replace every secret, then run:

```bash
docker compose --env-file runtime.env -f docker-compose.test.yml up -d
docker compose --env-file runtime.env -f docker-compose.test.yml ps
```

The default test gateway listens on port `8088`. MySQL, MinIO, the MinIO console, the backend, and kkFileView are not published directly.

## Stop or roll back

```bash
docker compose --env-file runtime.env -f docker-compose.test.yml down
```

Named volumes are retained by `down`. Do not use `down -v` unless the test data should be permanently deleted.
