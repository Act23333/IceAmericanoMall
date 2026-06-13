---@diagnostic disable: undefined-global
local c=tonumber(redis.call('GET',KEYS[1])or 0);
 return c>=tonumber(ARGV[1]) and -1 or c;