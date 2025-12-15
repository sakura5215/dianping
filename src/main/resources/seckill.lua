-- 秒杀下单资格判断（原子操作）
-- 仅负责：库存判断 + 一人一单判断 + 扣减库存 + 记录下单用户
-- 扣减成功返回 0 后，由 Java 侧将订单消息发送到 RabbitMQ，不在 Lua 内发消息
--
-- 返回值约定：
--   1 -> 库存不足
--   2 -> 用户已下过单（一人一单）
--   0 -> 资格判定通过，库存已扣减

-- 1.参数
-- 1.1 优惠券ID
local voucherId = ARGV[1]
-- 1.2 用户ID
local userId = ARGV[2]

-- 2.数据key
-- 2.1 优惠券库存Key
local stockKey = "seckill:voucher:" .. voucherId
-- 2.2 下单用户集合Key（用于一人一单判断）
local orderKey = "seckill:order:" .. voucherId

-- 3.脚本业务
-- 3.1 判断库存是否充足
if(tonumber(redis.call("get", stockKey)) <= 0) then
    -- 库存不足
    return 1
end

-- 3.2 判断用户是否已经下过单 SISMEMBER orderKey userId
if(redis.call("sismember", orderKey, userId) == 1) then
    -- 用户已经下过单，重复下单
    return 2
end

-- 3.3 扣减库存
redis.call("incrby", stockKey, -1)

-- 3.4 记录下单用户
redis.call("sadd", orderKey, userId)

return 0
