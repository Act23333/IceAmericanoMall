---@diagnostic disable: undefined-global
-- 限流Lua脚本：保证 INCR + EXPIRE 原子性执行
-- KEYS[1]: 限流的Redis Key
-- ARGV[1]: 过期时间(秒)

-- 1. 自增计数
local count = redis.call("INCR", KEYS[1])

-- 2. 第一次访问时，设置过期时间（仅执行1次）
if count == 1 then
    redis.call("EXPIRE", KEYS[1], tonumber(ARGV[1]))
end
