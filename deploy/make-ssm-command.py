"""Create a bounded SSM payload from checked-in deployment files. No secrets in it."""
import base64
import json
import os
import re
from pathlib import Path

tag = os.environ["IMAGE_TAG"]
registry = os.environ["ECR_REGISTRY"]
region = os.environ["AWS_REGION"]
mode = os.environ.get("DB_MODE", "database")
assert re.fullmatch(r"[a-f0-9]{40}", tag), "Expected a full commit SHA"
assert re.fullmatch(r"[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com", registry)
assert re.fullmatch(r"[a-z]{2}-[a-z]+-[0-9]+", region)
assert mode in ("database", "rds")
release = f"/opt/todo/releases/{tag}"
commands = ["set -eu", "umask 077", f"mkdir -p {release}"]
for name in ("compose.ec2.yml", "deploy.sh"):
    payload = base64.b64encode(Path("deploy", name).read_bytes()).decode()
    commands.append(f"printf '%s' '{payload}' | base64 -d > {release}/{name}")
commands.append(f"bash {release}/deploy.sh {registry} {tag} {region} {mode}")
Path("ssm-command.json").write_text(json.dumps({"commands": commands, "executionTimeout": ["900"]}), encoding="utf-8")
