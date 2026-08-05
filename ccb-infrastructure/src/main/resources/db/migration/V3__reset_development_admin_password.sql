UPDATE sys_user
SET password_hash = '${bootstrap_admin_password_hash}'
WHERE tenant_id = 1 AND id = 1 AND username = 'admin';