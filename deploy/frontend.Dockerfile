# syntax=docker/dockerfile:1.7
FROM --platform=$BUILDPLATFORM node:20-alpine AS builder
WORKDIR /workspace

COPY web/package.json web/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY web ./
RUN npm run build

FROM --platform=$TARGETPLATFORM nginx:1.27-alpine
COPY deploy/nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=builder /workspace/dist /usr/share/nginx/html

ENV NGINX_ENVSUBST_FILTER="^(APP_HOST|APP_PORT|FILES_HOST|PREVIEW_HOST)$"
EXPOSE 8088
