# AWS EC2 demo

This demo creates one public EC2 instance and runs the feature-store stack with
Docker Compose.

This is for demos only. It is not a production architecture.

## Cost guardrails

- Use a free-tier-eligible EC2 instance only if your account/region still has
  free-tier eligibility.
- Set `allowed_ssh_cidr` and `allowed_app_cidr` to your own public IP with `/32`.
- Keep the instance running only while demoing.
- Run `terraform destroy` immediately after the demo.
- Create an AWS Budget alert before starting.

The full stack is heavy for a micro instance because Kafka, PostgreSQL, Redis,
Spring Boot, Prometheus, and Grafana all need memory. The Terraform bootstrap
adds a 2 GB swap file so the demo can limp along. For smoother performance,
start without observability first, then enable Grafana/Prometheus only when you
need to show the dashboard.

## 1. Install tools

```bash
brew tap hashicorp/tap
brew install hashicorp/tap/terraform
brew install awscli
```

Verify:

```bash
terraform version
aws --version
docker --version
```

## 2. Configure AWS

```bash
aws configure
aws sts get-caller-identity
```

## 3. Create an EC2 key pair

If you already have an EC2 key pair, use that name in `terraform.tfvars`.

Otherwise create one from the repo root:

```bash
cd /Users/hkhichi/Documents/feature-store

aws ec2 create-key-pair \
  --key-name feature-store-demo \
  --query 'KeyMaterial' \
  --output text > deploy/aws-free-tier-demo/terraform/feature-store-demo.pem

chmod 400 deploy/aws-free-tier-demo/terraform/feature-store-demo.pem
```

## 4. Find your public IP

```bash
curl https://checkip.amazonaws.com
```

Use the returned IP as `x.x.x.x/32`.

## 5. Create the EC2 demo instance

```bash
cd /Users/hkhichi/Documents/feature-store/deploy/aws-free-tier-demo/terraform
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`:

```hcl
aws_region       = "us-east-1"
project_name     = "feature-store-demo"
environment      = "dev"
instance_type    = "t3.micro"
key_name         = "feature-store-demo"
allowed_ssh_cidr = "x.x.x.x/32"
allowed_app_cidr = "x.x.x.x/32"
```

Then run:

```bash
terraform init
terraform apply
```

Approve with `yes`.

Get the IP:

```bash
terraform output public_ip
```

## 6. Copy the project to EC2

From your laptop:

```bash
cd /Users/hkhichi/Documents/feature-store
EC2_IP=$(cd deploy/aws-free-tier-demo/terraform && terraform output -raw public_ip)

rsync -av \
  --exclude target \
  --exclude build \
  --exclude .git \
  -e "ssh -i deploy/aws-free-tier-demo/terraform/feature-store-demo.pem" \
  ./ ec2-user@$EC2_IP:/home/ec2-user/feature-store/
```

If your `.pem` file is somewhere else, update the `ssh -i` path.

## 7. Start the app stack

SSH in:

```bash
ssh -i deploy/aws-free-tier-demo/terraform/feature-store-demo.pem ec2-user@$EC2_IP
```

Run the API stack first:

```bash
cd ~/feature-store
docker compose -f compose.aws-demo.yml up -d --build
docker compose -f compose.aws-demo.yml ps
```

Check health:

```bash
curl http://localhost:8080/actuator/health
```

From your laptop:

```bash
curl http://$EC2_IP:8080/actuator/health
```

## 8. Optional: start Prometheus and Grafana

On the EC2 instance:

```bash
cd ~/feature-store
docker compose -f compose.aws-demo.yml --profile observability up -d --build
```

Then open:

- API: `http://EC2_PUBLIC_IP:8080`
- Grafana: `http://EC2_PUBLIC_IP:3000`
- Prometheus: `http://EC2_PUBLIC_IP:9090`

Grafana login is `admin` / `admin`.

## 9. Stop or destroy

For a short pause:

```bash
docker compose -f compose.aws-demo.yml --profile observability down
```

To remove AWS resources:

```bash
cd /Users/hkhichi/Documents/feature-store/deploy/aws-free-tier-demo/terraform
terraform destroy
```
