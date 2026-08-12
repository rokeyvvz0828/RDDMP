-- 所有菜单都可维护自身模块编码；一级菜单模块编码仍作为输入项配置的业务模块来源。
ALTER TABLE sys_menu
    MODIFY COLUMN module_key VARCHAR(64) NULL COMMENT '菜单对应的业务模块编码，一级菜单编码作为业务模块入口';
