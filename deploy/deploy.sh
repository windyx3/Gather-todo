#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
# This script runs as root through SSM. The workflow supplies validated values.
registry="${1:?ECR registry required}"
tag="${2:?image tag required}"
region="${3:?AWS region required}"
mode="${4:-database}"
[[ "$registry" =~ ^[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com$ ]] || exit 2
[[ "$tag" =~ ^[a-f0-9]{40}$ ]] || exit 2
[[ "$region" =~ ^[a-z]{2}-[a-z]+-[0-9]+$ ]] || exit 2
[[ "$mode" == database || "$mode" == rds ]] || exit 2
release="/opt/todo/releases/$tag"
mkdir -p /opt/todo/certs
exec 9>/opt/todo/deploy.lock
flock -w 600 9
cd "$release"
aws ssm get-parameter --name /todo/lab/env --with-decryption --region "$region" --query Parameter.Value --output text > secrets.env
chmod 600 secrets.env
export ECR_REGISTRY="$registry" IMAGE_TAG="$tag"
aws ecr get-login-password --region "$region" | docker login --username AWS --password-stdin "$registry"
compose() { docker compose --env-file "$release/secrets.env" -f "$release/compose.ec2.yml" "$@"; }
previous="$(readlink -f /opt/todo/current 2>/dev/null || true)"
rollback() {
  local code="$?"
  trap - ERR
  echo "Deployment failed; attempting application-image rollback."
  if [[ -n "$previous" && -f "$previous/release.env" && "$previous" != "$release" ]]; then
    (
      set -a
      # release.env contains only validated non-secret values generated below.
      source "$previous/release.env"
      set +a
      docker compose --env-file "$previous/secrets.env" -f "$previous/compose.ec2.yml" up -d --no-deps --force-recreate backend frontend
      docker compose --env-file "$previous/secrets.env" -f "$previous/compose.ec2.yml" up -d --wait --wait-timeout 180 backend frontend
    ) || echo "Rollback also failed. Inspect service health using SSM."
  else
    echo "No previous distinct release is available."
  fi
  exit "$code"
}
trap rollback ERR
compose pull backend frontend
if [[ "$mode" == database ]]; then
  compose --profile database up -d --wait --wait-timeout 120 postgres
else
  test -s /opt/todo/certs/global-bundle.pem
fi
# Recreate both containers so the frontend/backend release pair changes together.
compose up -d --no-deps --force-recreate backend frontend
compose up -d --wait --wait-timeout 180 backend frontend
curl --fail --silent http://127.0.0.1:8088/healthz > /dev/null
curl --fail --silent http://127.0.0.1:8088/api/version | python3 -c 'import json,os,sys; assert json.load(sys.stdin)["version"] == os.environ["IMAGE_TAG"]'
curl --fail --silent http://127.0.0.1:8088/version.json | python3 -c 'import json,os,sys; assert json.load(sys.stdin)["version"] == os.environ["IMAGE_TAG"]'
printf 'ECR_REGISTRY=%s\nIMAGE_TAG=%s\n' "$registry" "$tag" > release.env
ln -sfn "$release" /opt/todo/current
echo "Healthy release: $tag"
# Intentionally never delete database volumes or prune old images automatically.
