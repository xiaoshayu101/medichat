-- KEYS[1]: Redis里这个时段的号源key，如 slot:1001:2026-06-17:AM
-- ARGV[1]: 申请加的数量
-- ARGV[2]: 允许的最大总数（原始号源 × 1.2，由Java层计算好传进来）

--从 Redis 中读取当前该时段剩下的号源数量
local currentRemain = tonumber(redis.call('GET', KEYS[1]))

-- 如果Redis里没有这个key（号源还没同步过来），返回-2
if currentRemain == nil then
    return -2
end

local addCount = tonumber(ARGV[1])
local maxAllowed = tonumber(ARGV[2])

-- 加号后的总量 = 当前剩余 + 申请加的数量
local newRemain = currentRemain + addCount

if newRemain > maxAllowed then
    return -1  -- 超过上限，拒绝
end

-- 原子加号
redis.call('INCRBY', KEYS[1], addCount)
return newRemain  -- 返回加号后的剩余量