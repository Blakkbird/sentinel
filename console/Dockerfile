# Builds the web console and serves it with nginx.
#
# Node is only needed to turn the TypeScript and React sources into plain files a browser
# understands. It is left behind in stage 1; the final image is nginx plus those files,
# around 60 MB instead of a Node image's several hundred.
#
# Build it on its own with:  docker build -t sentinel-console ./console

# ---------- stage 1: build ----------
FROM node:22-alpine AS build
WORKDIR /build

# package-lock.json first, so Docker only reinstalls the libraries when it changes rather
# than on every edit to a component. "npm ci" installs exactly the locked versions.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# "build" type-checks everything with TypeScript first, then bundles, so a type error
# fails the image build instead of shipping.
RUN npm run build

# ---------- stage 2: serve ----------
FROM nginx:1.29-alpine AS runtime

# Replace the default welcome page configuration with ours.
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/console.conf
COPY --from=build /build/dist /usr/share/nginx/html

# 8080 rather than nginx's usual 80, to match the backend: nothing here needs a
# privileged port, and docker-compose.yml decides which port your laptop sees.
# nginx's own worker processes already drop to an unprivileged user.
EXPOSE 8080
