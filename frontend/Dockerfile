FROM caddy:2.10-alpine
# Port 8080 needs no bind capability; remove the image's file capability so
# Caddy also runs when the container capability bounding set is empty.
RUN setcap -r /usr/bin/caddy
COPY Caddyfile /etc/caddy/Caddyfile
# Build with ./scripts/frontend.sh build before packaging the static artifact.
COPY dist /srv
USER 10001:10001
EXPOSE 8080
