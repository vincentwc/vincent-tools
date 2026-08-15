local function version(key)
  local value = redis.call('GET', key)
  if not value then
    return '0'
  end
  return tostring(value)
end

local globalVersion = version(KEYS[1])
local tenantVersion = version(KEYS[2])
if globalVersion == ARGV[1] and tenantVersion == ARGV[2] then
  redis.call('SET', KEYS[3], ARGV[3], 'PX', ARGV[4])
  return 1
end
return 0
