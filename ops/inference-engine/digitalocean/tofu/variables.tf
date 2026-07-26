variable "project_name" {
  description = "Prefix used for DigitalOcean resources."
  type        = string
  default     = "socratic-tutor-inference"
}

variable "region" {
  description = "DigitalOcean region. The selected GPU plans are currently available in tor1."
  type        = string
  default     = "tor1"
}

variable "gpu_size" {
  description = "GPU Droplet slug. L40S is the default; RTX 6000 Ada is a compatible alternative."
  type        = string
  default     = "gpu-l40sx1-48gb"

  validation {
    condition     = contains(["gpu-l40sx1-48gb", "gpu-6000adax1-48gb"], var.gpu_size)
    error_message = "gpu_size must be gpu-l40sx1-48gb or gpu-6000adax1-48gb."
  }
}

variable "gpu_image" {
  description = "DigitalOcean NVIDIA single-GPU base image slug."
  type        = string
  default     = "gpu-h100x1-base"
}

variable "ssh_key_name" {
  description = "Name of an SSH public key already uploaded to DigitalOcean."
  type        = string
}

variable "admin_cidrs" {
  description = "CIDRs allowed to SSH. Defaults to public SSH with key authentication."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "inference_client_cidrs" {
  description = "CIDRs allowed to call llama-swap and Ollama directly. Keep empty when using an SSH tunnel."
  type        = list(string)
  default     = []

  validation {
    condition     = !contains(var.inference_client_cidrs, "0.0.0.0/0")
    error_message = "inference_client_cidrs must not expose inference publicly."
  }
}

variable "vpc_ip_range" {
  description = "Private CIDR when OpenTofu creates a dedicated inference VPC."
  type        = string
  default     = "10.42.0.0/20"
}

variable "existing_vpc_uuid" {
  description = "Existing tor1 application VPC UUID. When null, OpenTofu creates a dedicated VPC."
  type        = string
  default     = null
  nullable    = true
}

variable "volume_size_gib" {
  description = "Persistent block volume size for models, builds, and caches."
  type        = number
  default     = 100

  validation {
    condition     = var.volume_size_gib >= 50
    error_message = "volume_size_gib must be at least 50 GiB."
  }
}
