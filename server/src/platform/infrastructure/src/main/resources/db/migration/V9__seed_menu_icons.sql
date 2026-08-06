UPDATE sys_menu SET icon = CASE id
    WHEN 100 THEN 'setting'
    WHEN 101 THEN 'user'
    WHEN 102 THEN 'operation'
    WHEN 103 THEN 'org'
    WHEN 104 THEN 'menu'
    WHEN 105 THEN 'collection'
    WHEN 106 THEN 'document'
    WHEN 200 THEN 'grid'
    WHEN 201 THEN 'folder'
    WHEN 202 THEN 'tickets'
    WHEN 300 THEN 'setting'
    WHEN 301 THEN 'tools'
    WHEN 302 THEN 'monitor'
    WHEN 303 THEN 'connection'
    ELSE icon
END
WHERE tenant_id = 1 AND id IN (100, 101, 102, 103, 104, 105, 106, 200, 201, 202, 300, 301, 302, 303);
