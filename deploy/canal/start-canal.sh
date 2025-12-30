#!/bin/bash
# 启动 Canal Server（订阅 dianping 库 binlog）
# 前置：MySQL 已开 binlog（ROW 格式）、已建 canal 账号、Docker 可用
# 用法：bash start-canal.sh

CONF="D:/BaiduNetdiskDownload/heima-dianping/hm-dianping/deploy/canal/conf"

echo "[Canal] 启动 canal-server 容器"
docker run -d --name canal-server \
  -p 11111:11111 -p 11112:11112 \
  -v "$CONF/instance.properties":/home/admin/canal-server/conf/example/instance.properties \
  -v "$CONF/canal.properties":/home/admin/canal-server/conf/canal.properties \
  --restart unless-stopped \
  canal/canal-server:v1.1.7

echo "[Canal] 容器已启动，查看日志：docker logs -f canal-server"
