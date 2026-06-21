-- Adquire lock distribuído para cron jobs.
-- Retorna 1 se lock adquirido, 0 se já está travado por outra instância.
-- KEYS[1] = chave do lock (ex: "syax:billing:lock:commission-payout:2025-01")
-- ARGV[1] = identificador único desta instância (UUID + thread)
-- ARGV[2] = TTL em segundos
local result = redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', tonumber(ARGV[2]))
return result ~= false and 1 or 0