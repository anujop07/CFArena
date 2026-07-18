# ═══════════════════════════════════════════════════════════════════════════════
# CF Arena — Frontend Dockerfile (Multi-Stage Production Build)
# ═══════════════════════════════════════════════════════════════════════════════
#
# Stage 1 │ builder  — Node.js installs deps, runs `vite build`, produces dist/
# Stage 2 │ runtime  — Nginx serves the static dist/ files
#
# Why Nginx and not Node?
#   - Vite build output is 100% static (HTML + CSS + JS). No server needed.
#   - Nginx serves static files ~10x faster than Node, uses far less memory.
#   - Final image: ~25MB vs ~300MB if we kept Node.
# ═══════════════════════════════════════════════════════════════════════════════


# ─── Stage 1: Builder ─────────────────────────────────────────────────────────
# node:20-alpine = Node.js 20 LTS on Alpine Linux (slim, secure)
FROM node:20-alpine AS builder

WORKDIR /app

# ✅ LAYER CACHING TRICK (same principle as backend):
# Copy package files FIRST, install deps, THEN copy source.
# If package.json / package-lock.json haven't changed → Docker reuses the
# node_modules cache layer → saves 1-2 minutes per build.
COPY package.json package-lock.json ./

# ci = clean install, uses package-lock.json exactly (reproducible builds)
# --frozen-lockfile would be the pnpm equivalent
RUN npm ci

# ✅ Copy source code AFTER npm install (so code changes don't invalidate the npm cache)
COPY . .

# ─── Build Arguments for VITE_ environment variables ──────────────────────────
# VITE_ variables are BAKED INTO the JS bundle at build time (not runtime).
# They MUST be passed as Docker build args, not runtime env vars.
# In docker-compose / Jenkins, pass: --build-arg VITE_API_URL=https://api.yourdomain.com
ARG VITE_API_URL
ARG VITE_WS_URL
ARG VITE_GOOGLE_CLIENT_ID

# Make them available to Vite during the build
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_WS_URL=$VITE_WS_URL
ENV VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID

# Build the production bundle
# Output: /app/dist/ (static HTML + CSS + hashed JS chunks)
RUN npm run build


# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
# nginx:alpine = official Nginx on Alpine (~25MB total)
FROM nginx:alpine AS runtime

# Remove the default Nginx welcome page
RUN rm -rf /usr/share/nginx/html/*

# Copy our React build output into Nginx's serving directory
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy our custom Nginx config (handles React Router + security headers + gzip)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port 80 (Nginx default)
EXPOSE 80

# Nginx runs in the foreground — this is the exec form for proper signal handling
CMD ["nginx", "-g", "daemon off;"]
