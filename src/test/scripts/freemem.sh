#!/bin/bash
echo "Checking memory status"
free -m

echo "Swapping off"
swapoff -a

echo "Free cache"
sync;sysctl -w vm.drop_caches=3;sync

echo "Swapping on"
swapon -a

echo "Checking memory status"
free -m
