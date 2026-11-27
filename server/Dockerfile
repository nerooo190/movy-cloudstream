FROM node:24-bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates chromium \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev
COPY src ./src

ENV HOST=0.0.0.0 PORT=7000 CHROMIUM_PATH=/usr/bin/chromium
EXPOSE 7000
USER node
CMD ["npm", "start"]
