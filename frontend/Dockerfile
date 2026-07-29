# 멀티스테이지: vite로 정적 산출물을 만들고, nginx가 그걸 서빙 + /api를 백엔드로 넘긴다.
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# 토스 결제창 클라이언트 키. Vite는 빌드 시점에 import.meta.env를 리터럴로 치환하므로
# 런타임 환경변수로는 늦다 — 반드시 빌드 인자로 받아야 한다.
# .dockerignore가 .env.local을 빼기 때문에(로컬 파일을 이미지에 굽지 않는다) compose가
# .env의 TOSS_CLIENT_KEY를 build.args로 넘긴다. 안 넘어오면 빈 값으로 빌드되고 결제창만
# 안 열린다 — 나머지 화면은 그대로 뜨므로 빌드를 깨뜨리지는 않는다.
# 클라이언트 키는 공개값이라 번들에 박혀도 유출이 아니다. 시크릿 키(TOSS_SECRET_KEY)는
# 백엔드 전용이며 여기로 넘어오지 않는다.
ARG VITE_TOSS_CLIENT_KEY
ENV VITE_TOSS_CLIENT_KEY=$VITE_TOSS_CLIENT_KEY

RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
