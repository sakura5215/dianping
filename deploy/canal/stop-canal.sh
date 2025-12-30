#!/bin/bash
# 停止并移除 Canal Server 容器
docker stop canal-server 2>/dev/null
docker rm canal-server 2>/dev/null
echo "[Canal] 容器已停止并移除"
