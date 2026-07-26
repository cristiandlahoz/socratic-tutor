terraform {
  required_version = ">= 1.10.0"

  required_providers {
    digitalocean = {
      source  = "digitalocean/digitalocean"
      version = "~> 2.96.0"
    }
  }
}

# Authentication is environment-only. The Makefile exports DIGITALOCEAN_TOKEN
# from the git-ignored .env file, so the token never enters OpenTofu state.
provider "digitalocean" {}
