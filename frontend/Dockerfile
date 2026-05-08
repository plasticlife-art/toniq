FROM node:23-alpine AS build
WORKDIR /frontend

COPY frontend/package.json frontend/build.mjs ./
COPY frontend/src ./src

RUN npm run build

FROM nginx:1.27-alpine
WORKDIR /usr/share/nginx/html

COPY frontend/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY frontend/nginx/40-generate-config.sh /docker-entrypoint.d/40-generate-config.sh
COPY --from=build /frontend/dist /usr/share/nginx/html

ENV PUBLIC_API_BASE_URL=/api/public

RUN chmod +x /docker-entrypoint.d/40-generate-config.sh

EXPOSE 80
