output "public_ip" {
  description = "Public IP used only for operator SSH/Ansible."
  value       = digitalocean_droplet.inference.ipv4_address
}

output "private_ip" {
  description = "Private VPC address applications should use for inference."
  value       = digitalocean_droplet.inference.ipv4_address_private
}

output "openai_base_url" {
  description = "Private OpenAI-compatible llama-swap endpoint."
  value       = "http://${digitalocean_droplet.inference.ipv4_address_private}:8080/v1"
}

output "ollama_base_url" {
  description = "Private Ollama endpoint."
  value       = "http://${digitalocean_droplet.inference.ipv4_address_private}:11434"
}

output "volume_name" {
  description = "Volume name passed to Ansible for the stable device path."
  value       = digitalocean_volume.inference_data.name
}
