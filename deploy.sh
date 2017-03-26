#!/bin/bash

# Exit on any error
set -e

sudo /opt/google-cloud-sdk/bin/gcloud docker -- push asia.gcr.io/${PROJECT_NAME}/antlion-clojure:latest
sudo chown -R ubuntu:ubuntu /home/ubuntu/.kube
kubectl apply -f k8s/redis/
