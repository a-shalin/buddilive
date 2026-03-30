#!/bin/bash
set -e
cd "$(dirname "$0")"

if [ -f .env ]; then
	set -a
	source .env
	set +a
fi

cd mobile/android
./gradlew :app:assembleRelease
./publish-apk.sh

cd ../..
mvn clean package
cd ansible
ansible-playbook playbooks/deploy.yml "$@"
