-- quota_decr.lua
-- 原子性扣减号源，返回值：
--   1  = 扣减成功
--   0  = 号源已满
--  -1  = key 不存在（调用方应先预热）

local key = KEYS[1]

-- 1. 获取当前余量
local remaining = redis.call('GET', key)
if remaining == false then
    return -1   -- key 不存在，说明预热没做好
end

-- 2. 判断是否还有号
remaining = tonumber(remaining)
if remaining <= 0 then
    return 0    -- 已满
end

-- 3. 原子扣减
local afterDecr = redis.call('DECR', key)

-- 4. 二次校验：防止并发下 DECR 到负数
if afterDecr < 0 then
    redis.call('INCR', key)   -- 回滚
    return 0
end

return 1   -- 扣减成功