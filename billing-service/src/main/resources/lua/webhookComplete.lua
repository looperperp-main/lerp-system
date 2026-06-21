-- Atualiza status final do webhook (DONE ou ERROR).
-- Retorna 1 se atualizou, 0 se chave expirou (não bloqueia reprocessamento).
-- KEYS[1] = chave de idempotência
-- ARGV[1] = status final: 'DONE' | 'ERROR'
-- ARGV[2] = TTL em segundos (reinicia contagem)
local key = KEYS[1]
local status = ARGV[1]
local ttl = tonumber(ARGV[2])
if redis.call('EXISTS', key) == 1 then
    redis.call('SETEX', key, ttl, status)
    return 1
end
return 0