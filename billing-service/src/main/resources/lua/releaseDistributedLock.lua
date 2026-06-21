-- Libera lock apenas se a instância atual é a dona.
-- Retorna 1 se liberou, 0 se não é dona ou expirou.
-- KEYS[1] = chave do lock
-- ARGV[1] = identificador único desta instância (deve coincidir)
local key = KEYS[1]
local owner = ARGV[1]
local current = redis.call('GET', key)
if current == owner then
    redis.call('DEL', key)
    return 1
else
    return 0
end