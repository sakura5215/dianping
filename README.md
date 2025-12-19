# dianping

点评类社交平台后端 — 一个微服务化的高并发场景练手项目，覆盖「店铺查询 / 优惠券秒杀 / 达人探店 / 社交关注 / 附近店铺」五条主线业务。技术重点放在 **缓存策略 / 异步消息 / 分布式锁** 三大高可用主题的落地，按"先打稳底层，再做局部优化"的顺序迭代。

## 技术栈

| 层 | 选型 |
|---|---|
| Web | Spring Boot 3.2.10 / JDK 17 |
| 持久 | MyBatis-Plus 3.5.9 / MySQL 8 |
| 缓存 L1 | Caffeine（进程内本地缓存） |
| 缓存 L2 | Spring Data Redis 3.2 / Lettuce |
| 消息队列 | spring-boot-starter-amqp / RabbitMQ 3.13 |
| 分布式锁 | Redisson 3.22 |
| 工具 | Hutool 5.8 / Lombok |

## 缓存设计

读路径 `queryById` 走 L1 → L2 → DB 三级：

1. **L1 Caffeine**：进程内内存，`maximumSize=200` + `expireAfterWrite=10min`，命中零网络零序列化，承担热点店的绝大部分读。
2. **L2 Redis**：`CacheClient.queryWithLogicalExpire` 封装，逻辑过期 + 互斥锁双检：
   - 未命中 → 返回空（不打数据库）
   - 已命中未过期 → 直接返回旧值
   - 已命中已过期 → 抢锁后异步线程重建数据库回写，未抢锁线程先返回旧值，**防击穿**
3. **DB**：兜底来源。
4. 一致性：写路径 `update` 走 Cache-Aside，先 `updateById` 再删 Redis + Caffeine 双级；L2 逻辑过期异步重建期间 L1 的 10min TTL 兜底，最终一致。

## 异步秒杀

下单流程拆为「资格判定（同步）+ 订单落库（异步）」两阶段：

1. `seckill.lua` 在 Redis 原子执行：判一人一单 + 判库存 + 扣库存，返回 0/1/null。
2. Lua 返回 0 后 → Java 侧构造 `VoucherOrder` → `rabbitTemplate.convertAndSend` 投递到 `seckill.order.exchange` (direct)。
3. `SeckillOrderListener` `@RabbitListener` 消费 → Redisson 分布式锁防重复消费 → `createVoucherOrder` 落库（事务内一人一单校验 + 扣库存）。
4. broker 配置：`prefetch=1` 让多消费者平摊、`retry max-attempts=3` 应对偶发失败。

## 分布式锁

`SimpleRedisLock` + Redisson：
- 优惠券一人一单：Redisson 锁防并发下单。
- 消息消费幂等：购物消息进 listener 前先抢锁，同一订单号不会重复落库。

## Redis GEO 附近店铺

`queryShopByType` 用 Redis GEO 索引店铺坐标：`GEOSEARCH` 按距离排序 + 分页，避免 MySQL 算 haversine 全表扫。

## 本地启动

前置：MySQL 8 / Redis 6+ / RabbitMQ 3.13+。

### 后端

```bash
# 导入 schema
mysql -uroot -p < src/main/resources/db/dianping.sql

# 设置 MySQL 密码后启动（密码无默认值，需自行 export）
export MYSQL_PASSWORD=your_password
mvn spring-boot:run          # 监听 8081
```

### 前端

前端为纯静态页（原生 HTML + Vue2 + Element UI + axios），用 nginx 托管并通过 `/api` 反向代理到后端：

```bash
# 1. 把 frontend/ 目录复制到 nginx 的 html/frontend 下
# 2. 用 deploy/nginx.conf 启动 nginx（监听 8080，/api 反代到 8081）
nginx -c /path/to/deploy/nginx.conf

# 浏览器访问 http://localhost:8080/
```

前端 `js/common.js` 中 `axios.defaults.baseURL = "/api"`，由 nginx `location /api` 去掉前缀后转发到后端，与后端 `@RequestMapping` 无 `/api` 前缀对应。

环境变量（在 `application.yml` 中以 `${ENV:default}` 形式声明，未设置就用默认值）：

| 变量 | 默认 | 说明 |
|---|---|---|
| `MYSQL_HOST/PORT/DB/USER/PASSWORD` | localhost/3306/dianping/root/（无默认） | MySQL 连接，密码需自行 export |
| `REDIS_HOST/PORT` | localhost/6379 | Redis 连接 |
| `RABBITMQ_HOST/PORT/USER/PASSWORD/VHOST` | localhost/5672/guest/guest// | RabbitMQ 连接 |
| `DIANPING_UPLOAD_PATH` | ./uploads | 图片上传目录 |

> 密码类信息均不提供默认值，启动前请通过环境变量注入（如 `export MYSQL_PASSWORD=xxx`），或创建 `application-local.yml` 覆盖。

## 目录结构

```
src/main/java/com/dianping
├── config/         # MVC/Redisson/MQ/Caffeine 配置
├── controller/     # REST 入口
├── service/impl/   # 业务实现（含 Shop 多级缓存、Voucher + VoucherOrder 秒杀）
├── mq/             # RabbitMQ 消费者
├── utils/          # RedisConstants / CacheClient（封装穿透/击穿方案）/ RedisIdWorker
└── DianPingApplication.java

frontend/           # 前端静态页（HTML + Vue2 + Element UI + axios）
├── *.html          # 首页/登录/店铺详情/博客详情等页面
├── js/             # common.js（axios 配置）/ vue.js / element.js
├── css/            # 样式
└── imgs/           # 图片资源

deploy/
└── nginx.conf      # 前端托管 + /api 反向代理配置
```