# family-os-mcp (공식 MCP SDK / TypeScript / Streamable HTTP) — 설계: phase-drafts.md (Phase 6)
FROM node:20-slim
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
EXPOSE 3000
CMD ["npm", "start"]
