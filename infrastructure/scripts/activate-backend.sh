#!/usr/bin/env bash
set -euo pipefail

release=${1:?Release identifier is required}
[[ "$release" =~ ^[a-f0-9]+-[0-9]+-[0-9]+$ ]] || exit 1
base=/opt/nakorn-thai/backend
destination="$base/releases/$release"
test -s "$destination/backend.jar"
previous=$(readlink -e "$base/current" || true)

activate() {
  ln -s "$1" "$base/current-$release"
  mv -Tf "$base/current-$release" "$base/current"
}

healthy() {
  for attempt in {1..30}; do
    if systemctl is-active --quiet nakorn-thai-backend.service &&
      curl --fail --silent --show-error --max-time 3 \
        http://127.0.0.1:8081/actuator/health >/dev/null; then
      return 0
    fi
    sleep 2
  done
  return 1
}

activate "$destination"
if sudo -n /usr/bin/systemctl restart nakorn-thai-backend.service && healthy; then
  echo "Backend release $release is healthy."
  exit 0
fi

echo 'Backend deployment failed; restoring the previous release.' >&2
if [[ -n "$previous" && -s "$previous/backend.jar" ]]; then
  activate "$previous"
  sudo -n /usr/bin/systemctl restart nakorn-thai-backend.service
  healthy || echo 'Previous release is also unhealthy; inspect service logs.' >&2
else
  sudo -n /usr/bin/systemctl stop nakorn-thai-backend.service
  rm -f "$base/current"
fi
exit 1
