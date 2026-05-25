output "public_ip" {
  description = "Public IPv4 address for the demo EC2 instance."
  value       = aws_instance.demo.public_ip
}

output "ssh_command" {
  description = "SSH command template."
  value       = "ssh -i /path/to/key.pem ec2-user@${aws_instance.demo.public_ip}"
}

output "api_url" {
  description = "Feature Store API URL after Docker Compose is running."
  value       = "http://${aws_instance.demo.public_ip}:8080"
}

output "grafana_url" {
  description = "Grafana URL after starting the observability profile."
  value       = "http://${aws_instance.demo.public_ip}:3000"
}

output "prometheus_url" {
  description = "Prometheus URL after starting the observability profile."
  value       = "http://${aws_instance.demo.public_ip}:9090"
}
