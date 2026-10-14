FROM node:22-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY server.mjs ./
USER node
ENV BIND=0.0.0.0 PORT=8787
EXPOSE 8787
CMD ["node", "server.mjs"]
