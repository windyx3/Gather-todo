FROM node:24-alpine AS build
WORKDIR /build
COPY package*.json ./
RUN npm ci
COPY index.html vite.config.js ./
COPY src src
RUN npm run build
ARG APP_VERSION=local
RUN printf '{"version":"%s"}\n' "$APP_VERSION" > dist/version.json

FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=15s --retries=6 CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
