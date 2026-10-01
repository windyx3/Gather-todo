# AWS deployment lab record

This was a temporary deployment exercise in `us-east-2`. Resources were operated by the project owner. On 2026-10-01, the owner ended the experiment and reported resource deletion; the checklist below records the remaining items to verify rather than claiming an independent AWS inventory audit.

## Deployment path

1. GitHub Actions ran backend tests, PostgreSQL integration tests, frontend checks, browser tests and a container-stack smoke test.
2. GitHub OIDC supplied temporary AWS role credentials for publishing to ECR and sending deployment commands through SSM.
3. EC2 retrieved runtime secrets from Parameter Store and pulled immutable commit-tagged images.
4. Nginx served the frontend and proxied API requests to Spring Boot. Browser access used an SSM tunnel to the instance's loopback-bound port 8088.
5. The application initially used a PostgreSQL container with a persistent volume, then used private RDS with TLS certificate verification.

An infrastructure template is included in `deploy/infrastructure.yml`. The recorded lab used console/CLI setup; this record does not claim the template was deployed and validated as a CloudFormation stack.

## RDS verification

The final verified application release was:

```text
5937f651ba95a9561550a7912296c657d45db946
```

The RDS server reported PostgreSQL 18.3. Connectivity was checked with a PostgreSQL 18 client, using database `todo`, role `todo_app`, port 5432 and `sslmode=verify-full`. The client reported TLS 1.3.

The application role was configured with:

```sql
CREATE ROLE todo_app
  LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
GRANT CONNECT ON DATABASE todo TO todo_app;
GRANT USAGE, CREATE ON SCHEMA public TO todo_app;
```

Its password was set interactively with `\password todo_app`. The role can create schema objects because application startup runs Flyway migrations. A production design can separate migration privileges from the runtime role.

Runtime configuration used:

```dotenv
DB_USER=todo_app
DB_PASSWORD=<application-role-password>
DB_URL=jdbc:postgresql://<rds-endpoint>:5432/todo?sslmode=verify-full&sslrootcert=/certs/global-bundle.pem
JWT_SECRET=<existing-signing-secret>
COOKIE_SECURE=false
```

The RDS CA bundle was mounted read-only into the backend at `/certs/global-bundle.pem`. The RDS security group allowed PostgreSQL traffic from the EC2 application security group. The database was configured without public access.

The following query returned migration version `1`, description `accounts projects tasks`, and `success = t`:

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

A task created through the web application was found directly in RDS:

```sql
SELECT p.name AS project, t.title, t.completed
FROM projects p
JOIN tasks t ON t.project_id = p.id
WHERE p.name = 'rds-check';
```

Observed result:

```text
project    | title                | completed
rds-check  | RDS persistence test | f
```

After stopping the old EC2 PostgreSQL container, both application containers remained healthy and `/healthz` returned `status: UP`. These checks established the application-to-RDS path. They do not establish PostgreSQL 18 coverage in CI; `PostgresIT` still uses PostgreSQL 17.

## Configuration incident and recovery

The first RDS deployment failed at startup with `Connection to localhost:5432 refused`. Inspection of only `DB_URL` and `DB_USER` showed:

```dotenv
DB_USER=todo
DB_URL=jdbc:postgresql://:5432/todo?sslmode=verify-full&sslrootcert=/certs/global-bundle.pem
```

The shell variable used to generate the URL was empty, so the URL lacked a host. The username also still referred to the old database role.

Recovery consisted of copying the actual RDS endpoint, checking the generated URL, changing the three database settings in the existing SSM parameter, and redeploying the same image release. The JWT signing secret was preserved. The deployment then reported `Healthy release` and the RDS queries above succeeded.

The same-release deployment failure printed `No previous distinct release is available`. The script only automatically attempts image rollback to a different release. A protected backup of the old configuration had been prepared before the database switch; image rollback alone is not a database/configuration recovery procedure.

## Cleanup checklist

Keep GitHub `AWS_DEPLOY_ENABLED=false`. Check the deployment account and region before removing resources. Remove only resources belonging to this lab:

- RDS `gather-todo-db`: deletion complete; no final snapshot or retained automated backup required for this disposable dataset. Check for separately retained/manual backups.
- EC2 `GatherTodoLab`: instance terminated; its recorded EBS volumes removed, including any volume not configured for automatic deletion on termination.
- ECR repositories `todo-backend` and `todo-frontend`: repositories and images removed.
- Parameter Store `/todo/lab/env`: parameter removed.
- Security groups `GatherTodoRds-sg` and `GatherTodoLab-sg`: removed after their dependencies are gone.
- IAM roles `GatherTodoGitHubRole` and `GatherTodoEc2Role`: removed, including the experiment's EC2 instance profile.
- GitHub OIDC provider: remove only if no other project uses it.
- Any manually allocated Elastic IP or snapshots created for the experiment: verify ownership before removing. The recorded lab did not intentionally allocate an Elastic IP.
- Close the local SSM port-forwarding session.

Preserve the default VPC, default security group, AWS-managed policies/keys and unrelated project resources. A zero or incomplete bill display is not proof that all billable resources have been removed. Historical experiment charges remain after cleanup.

References: [RDS deletion and backup handling](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_DeleteInstance.html), [EC2 termination](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/terminating-instances.html), [EBS volume deletion](https://docs.aws.amazon.com/ebs/latest/userguide/ebs-deleting-volume.html), [ECR repository deletion](https://docs.aws.amazon.com/AmazonECR/latest/userguide/repository-delete.html), [IAM role deletion](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_manage_delete.html).
