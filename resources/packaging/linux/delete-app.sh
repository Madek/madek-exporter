#!/usr/bin/env bash
# Remove the Madek Exporter apt package installed from the .deb in this folder.
set -euo pipefail
sudo apt remove madek-exporter
