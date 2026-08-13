-- Bootstrap / change staff roles in Neon (SQL Editor).
-- Roles: DRIVER | EMPLOYEE | ADMIN

-- You as ADMIN
INSERT INTO app_users (phone, name, role, wallet_coins, created_at)
VALUES ('9845134394', 'Admin', 'ADMIN', 0, NOW())
ON CONFLICT (phone) DO UPDATE SET role = 'ADMIN';

-- Employee
INSERT INTO app_users (phone, name, role, wallet_coins, created_at)
VALUES ('9448166221', 'Employee', 'EMPLOYEE', 0, NOW())
ON CONFLICT (phone) DO UPDATE SET role = 'EMPLOYEE';

-- Check
SELECT phone, name, role FROM app_users WHERE phone IN ('9845134394', '9448166221');
