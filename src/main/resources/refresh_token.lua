
-- Cơ chế refresh token rotation, khi refresh trả lại cả 2 accesstoken và refresh token
-- Dùng lua script vì có tính nguyên tử, nếu ko cùng 1 token cả 2 đều pass qua mất

local data = redis.call('HMGET', KEYS[1], 'userId', 'familyId', 'usedAt')

--KEYS[1] là key mà backend truyền vào
--data[1] = userId     -- ví dụ "user-456"
--data[2] = familyId   -- ví dụ "family-xyz"
--data[3] = usedAt     -- ví dụ "1729500000" hoặc nil (usedAt là tg lúc token bị sử dụng)


if not data[1] then return 'NONE' end

--redis.call('TIME') — lệnh Redis TIME trả về mảng [seconds, microseconds]
--Tại sao dùng TIME của Redis thay vì now() của Lua?
-- + Đồng bộ giữa các instance: Nếu app chạy nhiều pod/container, mỗi pod có đồng hồ riêng có thể lệch.
--      Redis là single source of truth cho thời gian → tất cả pod dùng chung timestamp.
-- + Tránh bị ảnh hưởng bởi clock skew giữa các server.
local now = tonumber(redis.call('TIME')[1])

--Grace period để làm gì?
--Client có thể gửi cùng 1 refresh request 2 lần do: network timeout retry, user mở nhiều tab, mobile app retry.
--Nếu không có grace period → lần thứ 2 bị coi là tấn công → user bị logout oan.
--Grace period (thường 5-30 giây) cho phép request trùng trong thời gian ngắn → trả lại cùng response thay vì báo lỗi.

-- Nếu token được sử dụng rồi mà còn sử dụng lại thì xét đk dưới đây
if data[3] then
    -- nếu gtrị tg (now - usedAt) <= grace period time (trong thời gian ân hạn) thì cho phép gọi lại
    if now - tonumber(data[3]) <= tonumber(ARGV[1]) then
        return 'GRACE|' .. data[1] .. '|' .. data[2]
    end
    return 'REUSED|' .. data[1] .. '|' .. data[2] -- ngược lại có thể bị attack -> revoke family
end

-- đánh dấu token đã dùng và trả kết quả ok
redis.call('HSET',KEYS[1], 'usedAt', now)
return 'OK|' .. data[1] .. '|' .. data[2]