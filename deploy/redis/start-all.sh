#!/bin/bash
# 启动 Redis 主从 + 哨兵（本地演示用）
# 用法：bash start-all.sh
# 注意：进程以前台方式常驻，请通过 run_in_background 启动，或开多个终端

REDIS="D:/software/Redis-x64-3.2.100/redis-server.exe"
BASE="D:/BaiduNetdiskDownload/heima-dianping/hm-dianping/deploy/redis"

echo "[1/6] 启动主节点 6379"
"$REDIS" "$BASE/6379/redis.conf" &
sleep 1

echo "[2/6] 启动从节点 6380"
"$REDIS" "$BASE/6380/redis.conf" &
sleep 1

echo "[3/6] 启动从节点 6381"
"$REDIS" "$BASE/6381/redis.conf" &
sleep 1

echo "[4/6] 启动哨兵 26379"
"$REDIS" "$BASE/sentinel-26379/sentinel.conf" --sentinel &
sleep 1

echo "[5/6] 启动哨兵 26380"
"$REDIS" "$BASE/sentinel-26380/sentinel.conf" --sentinel &
sleep 1

echo "[6/6] 启动哨兵 26381"
"$REDIS" "$BASE/sentinel-26381/sentinel.conf" --sentinel &

echo "全部启动完成，进程保持前台运行"
# 保持前台，让 run_in_background 认为任务还在跑
wait
