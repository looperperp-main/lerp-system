-- Tenta marcar webhook como "em processamento" de forma atômica.
-- Retorna 1 se pode processar (primeiro acesso), 0 se já existe (duplicata).
-- KEYS[1] = chave de idempotência
-- ARGV[1] = TTL em segundos
local key = KEYS[1]
local ttl = tonumber(ARGV[1])
local result = redis.call('SET', key, 'PROCESSING', 'NX', 'EX', ttl)
if result then
    return 1
else
    return 0
end