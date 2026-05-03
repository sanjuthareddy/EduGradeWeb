#!/bin/bash
curl -fsSL https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz | tar xz
export PATH=$PWD/apache-maven-3.9.6/bin:$PATH
mvn package -q
