#!/bin/bash
# Install non-Maven-Central JARs to local Maven repository.
# Run this once before building with Maven.

set -e

mvn install:install-file \
  -Dfile=WebContent/WEB-INF/lib/moss-common-1.0-1-g9ef29ef.jar \
  -DgroupId=ca.digitalcave.moss \
  -DartifactId=moss-common \
  -Dversion=1.0-1-g9ef29ef \
  -Dpackaging=jar

mvn install:install-file \
  -Dfile=WebContent/WEB-INF/lib/moss-crypto-1.0-22-gb0bf44e.jar \
  -DgroupId=ca.digitalcave.moss \
  -DartifactId=moss-crypto \
  -Dversion=1.0-22-gb0bf44e \
  -Dpackaging=jar

mvn install:install-file \
  -Dfile=WebContent/WEB-INF/lib/moss-restlet-1.0-136-g75f01f6.jar \
  -DgroupId=ca.digitalcave.moss \
  -DartifactId=moss-restlet \
  -Dversion=1.0-136-g75f01f6 \
  -Dpackaging=jar

echo "Local dependencies installed successfully."
