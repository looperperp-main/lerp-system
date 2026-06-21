-- Garante que comissão anual seja gerada no máximo uma vez por ano por (parceiro, tenant).
-- Retorna 1 se é a primeira vez este ano, 0 se já foi gerada.
-- KEYS[1] = "syax:billing:commission:annual:{partnerId}:{tenantId}:{year}"
-- ARGV[1] = TTL em segundos (ex: 31536000 = 1 ano)
local result = redis.call('SET', KEYS[1], '1', 'NX', 'EX', tonumber(ARGV[1]))
return result ~= false and 1 or 0