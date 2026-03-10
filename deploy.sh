#!/bin/bash
set -e
cd "$(dirname "$0")"
mvn clean package
cd ansible
ansible-playbook playbooks/deploy.yml "$@"
