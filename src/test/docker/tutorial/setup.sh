#!/bin/bash
shopt -s expand_aliases
git clone git@github.com:librairy/client.git
cd client
git checkout develop
alias test='docker run -it --name librairy-tutorial -v "$PWD":/usr/src/mymaven -w /usr/src/mymaven maven:3.5.2-jdk-8-alpine mvn clean test'
test
