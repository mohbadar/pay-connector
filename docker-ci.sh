#!/bin/bash

COMMIT=$(git rev-parse HEAD)
IMAGE="govukpay/connector:$COMMIT"
mvn clean package && docker build -t $IMAGE . && docker push $IMAGE
