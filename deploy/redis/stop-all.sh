#!/bin/bash
# 停止所有 Redis 主从 + 哨兵实例
REDIS_CLI="D:/software/Redis-x64-3.2.100/redis-cli.exe"

echo "关闭哨兵"
"$REDIS_CLI" -p 26379 shutdown nosave 2>/dev/null
"$REDIS_CLI" -p 26380 shutdown nosave 2>/dev/null
"$REDIS_CLI" -p 26381 shutdown nosave 2>/dev/null

echo "关闭从节点"
"$REDIS_CLI" -p 6380 shutdown nosave 2>/dev/null
"$REDIS_CLI" -p 6381 shutdown nosave 2>/dev/null

echo "关闭主节点"
"$REDIS_CLI" -p 6379 shutdown nosave 2>/dev/null

echo "全部已关闭"
