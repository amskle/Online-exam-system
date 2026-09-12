-- 补齐 1000 个压测学生账号（密码统一为 123456，复用默认管理员 BCrypt 哈希）
-- 用法: mysql -u root -p exam < seed_loadtest_accounts.sql
-- 已有账号保持不变，可重复执行；登录压测前还需运行 seed_jmeter_accounts.py 预置可信设备。

INSERT INTO user (account, password, username, role, email, email_verify_time, login_status, create_time)
SELECT
  CONCAT('loadtest_', LPAD(nums.n, 4, '0')),
  '$2b$10$P2rqMDKks/zYfWA.i4f15.3NHkX2tdgECbcFdDNS6VWFK38fiOPVq',
  CONCAT('压测账号', LPAD(nums.n, 4, '0')),
  1,
  CONCAT('loadtest_', LPAD(nums.n, 4, '0'), '@test.local'),
  NOW(),
  0,
  NOW()
FROM (
  SELECT (h.n * 100 + t.n * 10 + u.n + 1) AS n
  FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
        UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
        UNION ALL SELECT 8 UNION ALL SELECT 9) h
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
        UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
        UNION ALL SELECT 8 UNION ALL SELECT 9) t
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
        UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
        UNION ALL SELECT 8 UNION ALL SELECT 9) u
) nums
WHERE nums.n <= 1000
  AND NOT EXISTS (
    SELECT 1 FROM user existing
    WHERE existing.account = CONCAT('loadtest_', LPAD(nums.n, 4, '0'))
  );

-- 查看生成的账号（用于确认）
SELECT id, account, role, login_status, email_verify_time
FROM user
WHERE account LIKE 'loadtest_%'
ORDER BY id
LIMIT 5;
