FROM node:24-alpine AS build

WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./frontend/
RUN npm --prefix frontend ci

COPY frontend ./frontend
COPY frontend-holdem ./frontend-holdem
COPY frontend-omaha ./frontend-omaha
COPY frontend-pineapple ./frontend-pineapple
COPY frontend-five-card ./frontend-five-card
COPY frontend-razz ./frontend-razz

ARG VARIANT_DIR
RUN npm --prefix frontend exec -- vite build --config ./${VARIANT_DIR}/vite.config.ts

FROM nginx:1.30-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
ARG VARIANT_DIR
COPY --from=build /app/${VARIANT_DIR}/dist /usr/share/nginx/html
EXPOSE 80
