#!/usr/bin/env bash
set -euo pipefail
command_id="${1:?}"
instance_id="${2:?}"
for attempt in $(seq 1 180); do
  status="$(aws ssm get-command-invocation --command-id "$command_id" --instance-id "$instance_id" --query Status --output text 2>/dev/null || true)"
  case "$status" in
    Success) echo "EC2 deployment succeeded"; exit 0 ;;
    Failed|TimedOut|Cancelled|Cancelling)
      aws ssm get-command-invocation --command-id "$command_id" --instance-id "$instance_id" --query '{Status:Status,Error:StandardErrorContent,Output:StandardOutputContent}'
      exit 1 ;;
  esac
  sleep 5
done
echo "SSM command did not finish before the workflow timeout"
exit 1
