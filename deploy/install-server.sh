#!/usr/bin/env bash
set -e
apt update
apt install -y openjdk-21-jdk maven postgresql nginx git ufw
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 5050/tcp
echo "Server base installation completed."
