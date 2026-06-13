---@diagnostic disable: undefined-global
-- 公共Redis键删除脚本
-- 功能：判断Key是否存在，存在则删除并返回1，不存在返回0
-- 参数：KEYS[1] - 需要删除的Redis键

-- 检查键是否存在
if redis.call('EXISTS', KEYS[1]) == 1 then
    -- 存在则删除键
    redis.call('DEL', KEYS[1])
    -- 返回0：删除成功
    return 0
else
    -- 返回1：键不存在
    return 1
end