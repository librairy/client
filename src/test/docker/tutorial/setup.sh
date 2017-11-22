#!/bin/bash
git clone git@github.com:librairy/client.git
cd client
git checkout develop
docker run -it --rm --name librairy-tutorial -v "$PWD":/usr/src/mymaven -w /usr/src/mymaven maven:3.2-jdk-8 mvn clean install

