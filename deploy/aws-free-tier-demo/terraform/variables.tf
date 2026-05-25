variable "aws_region" {
  description = "AWS region for the demo EC2 instance."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Prefix used for AWS resource names."
  type        = string
  default     = "feature-store-demo"
}

variable "environment" {
  description = "Environment label."
  type        = string
  default     = "dev"
}

variable "instance_type" {
  description = "EC2 instance type. t3.micro/t2.micro are commonly used for free-tier demos when eligible."
  type        = string
  default     = "t3.micro"
}

variable "key_name" {
  description = "Existing EC2 key pair name used for SSH."
  type        = string
}

variable "allowed_ssh_cidr" {
  description = "CIDR allowed to SSH to the instance. Use your public IP as x.x.x.x/32."
  type        = string
}

variable "allowed_app_cidr" {
  description = "CIDR allowed to reach app/demo ports. Use your public IP as x.x.x.x/32 for safest demo access."
  type        = string
}

variable "root_volume_size" {
  description = "Root EBS volume size in GB. Stay within your account's free-tier EBS allowance."
  type        = number
  default     = 20
}
