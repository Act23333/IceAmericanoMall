---@diagnostic disable: undefined-global
-- 限流Lua脚本：保证 INCR + EXPIRE 原子性执行
-- KEYS[1]: 限流的Redis Key
-- ARGV[1]: 最大允许访问次数(limit)
-- ARGV[2]: 过期时间(秒)

-- 1. 自增计数
local count = redis.call("INCR", KEYS[1])

-- 2. 第一次访问时，设置过期时间（仅执行1次）
if count == 1 then
    redis.call("EXPIRE", KEYS[1], tonumber(ARGV[2]))
end

-- 3. 判断是否超过限流阈值
-- 返回 1=超限，0=正常
if count > tonumber(ARGV[1]) then
    return 1
else
    return 0
end