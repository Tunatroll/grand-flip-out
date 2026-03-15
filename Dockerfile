# Grand Flip Out API + website — build from repo root for Railway or any container host
FROM node:20-alpine

WORKDIR /app
COPY server/package.json server/package-lock.json* ./server/
RUN cd server && npm ci --omit=dev

COPY server ./server
COPY website ./server/website

WORKDIR /app/server
ENV NODE_ENV=production
ENV PORT=3000
ENV WEBSITE_DIR=/app/server/website
EXPOSE 3000

CMD ["node", "index.js"]
