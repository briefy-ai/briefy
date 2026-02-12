#!/bin/sh
# Extract nameserver from /etc/resolv.conf for Nginx resolver directive
# Wrap IPv6 addresses in brackets as required by Nginx
NAMESERVER=$(awk '/^nameserver/{print $2; exit}' /etc/resolv.conf)
case "$NAMESERVER" in
  *:*) NAMESERVER="[${NAMESERVER}]" ;;  # IPv6 needs brackets
esac
sed -i "s|__RESOLVER__|${NAMESERVER:-8.8.8.8}|" /etc/nginx/conf.d/default.conf
exec nginx -g 'daemon off;'
