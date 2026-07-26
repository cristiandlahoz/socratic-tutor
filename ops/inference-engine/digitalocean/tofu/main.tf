data "digitalocean_ssh_key" "operator" {
  name = var.ssh_key_name
}

resource "digitalocean_vpc" "inference" {
  count = var.existing_vpc_uuid == null ? 1 : 0

  name     = "${var.project_name}-vpc"
  region   = var.region
  ip_range = var.vpc_ip_range
}

resource "digitalocean_volume" "inference_data" {
  region                   = var.region
  name                     = "${var.project_name}-data"
  size                     = var.volume_size_gib
  initial_filesystem_type  = "ext4"
  initial_filesystem_label = "inference-data"
  description              = "Persistent model, build, and Ollama cache"

  lifecycle {
    prevent_destroy = true
  }
}

resource "digitalocean_droplet" "inference" {
  name              = var.project_name
  region            = var.region
  size              = var.gpu_size
  image             = var.gpu_image
  ssh_keys          = [data.digitalocean_ssh_key.operator.id]
  vpc_uuid          = var.existing_vpc_uuid != null ? var.existing_vpc_uuid : digitalocean_vpc.inference[0].id
  volume_ids        = [digitalocean_volume.inference_data.id]
  monitoring        = true
  backups           = false
  ipv6              = false
  graceful_shutdown = true
  tags              = ["socratic-tutor", "inference", "gpu"]
}

resource "digitalocean_firewall" "inference" {
  name        = "${var.project_name}-firewall"
  droplet_ids = [digitalocean_droplet.inference.id]

  inbound_rule {
    protocol         = "tcp"
    port_range       = "22"
    source_addresses = var.admin_cidrs
  }

  dynamic "inbound_rule" {
    for_each = length(var.inference_client_cidrs) > 0 ? [1] : []
    content {
      protocol         = "tcp"
      port_range       = "8080"
      source_addresses = var.inference_client_cidrs
    }
  }

  dynamic "inbound_rule" {
    for_each = length(var.inference_client_cidrs) > 0 ? [1] : []
    content {
      protocol         = "tcp"
      port_range       = "11434"
      source_addresses = var.inference_client_cidrs
    }
  }

  outbound_rule {
    protocol              = "tcp"
    port_range            = "1-65535"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }

  outbound_rule {
    protocol              = "udp"
    port_range            = "1-65535"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }

  outbound_rule {
    protocol              = "icmp"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }
}

resource "digitalocean_project" "inference" {
  name        = var.project_name
  description = "GPU inference for Socratic Tutor"
  purpose     = "Service or API"
  environment = "Production"
  resources = [
    digitalocean_droplet.inference.urn,
    digitalocean_volume.inference_data.urn,
  ]
}
