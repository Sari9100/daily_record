# Family OS 프론트 (Expo web static → nginx) — 설계: phase-drafts.md (Phase 4)
# app.json web.output=static → `expo export` 산출물은 dist/

FROM node:20 AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# API base 를 빌드 시 주입 (config.ts 가 EXPO_PUBLIC_API_BASE_URL 를 읽음)
ARG EXPO_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
ENV EXPO_PUBLIC_API_BASE_URL=$EXPO_PUBLIC_API_BASE_URL
RUN npx expo export -p web

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
