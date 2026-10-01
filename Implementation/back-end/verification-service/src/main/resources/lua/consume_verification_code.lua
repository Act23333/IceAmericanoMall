-- 原子「比对并消费」验证码（verification-service）
-- KEYS[1] = 验证码 Key
-- ARGV[1] = 用户提交的验证码
-- 返回: 1 = 匹配且已删除（一次性消费成功）; 0 = 不匹配或不存在
--
-- 为什么必须走 Lua：GET 与 DEL 之间存在时间窗，并发/重放请求可能让同一个验证码
-- 被两次判定为「正确」；Lua 脚本在 Redis 单线程内原子执行，彻底关闭该窗口。
local cached = redis.call('GET', KEYS[1])
if not cached then
    return 0
end
if cached == ARGV[1] then
    redis.call('DEL', KEYS[1])
    return 1
end
return 0
