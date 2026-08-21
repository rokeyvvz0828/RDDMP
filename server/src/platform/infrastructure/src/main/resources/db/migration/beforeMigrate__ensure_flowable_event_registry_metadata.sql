-- 空库执行 V24 前，Flowable schema update 已关闭，无法由框架预先创建 Liquibase 元数据表。
-- 本 callback 仅补齐 V24 的既有前置假设；所有语句必须幂等，且不得写 flyway_schema_history。

SET @create_flowable_changelog_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE TABLE `FLW_EV_DATABASECHANGELOG` (`ID` VARCHAR(255) COLLATE utf8mb3_bin NOT NULL, `AUTHOR` VARCHAR(255) COLLATE utf8mb3_bin NOT NULL, `FILENAME` VARCHAR(255) COLLATE utf8mb3_bin NOT NULL, `DATEEXECUTED` DATETIME NOT NULL, `ORDEREXECUTED` INT NOT NULL, `EXECTYPE` VARCHAR(10) COLLATE utf8mb3_bin NOT NULL, `MD5SUM` VARCHAR(35) COLLATE utf8mb3_bin DEFAULT NULL, `DESCRIPTION` VARCHAR(255) COLLATE utf8mb3_bin DEFAULT NULL, `COMMENTS` VARCHAR(255) COLLATE utf8mb3_bin DEFAULT NULL, `TAG` VARCHAR(255) COLLATE utf8mb3_bin DEFAULT NULL, `LIQUIBASE` VARCHAR(20) COLLATE utf8mb3_bin DEFAULT NULL, `CONTEXTS` VARCHAR(255) COLLATE utf8mb3_bin DEFAULT NULL, `LABELS` VARCHAR(255) COLLATE utf8mb3_bin DEFAULT NULL, `DEPLOYMENT_ID` VARCHAR(10) COLLATE utf8mb3_bin DEFAULT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin',
        'DO 0'
    )
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'FLW_EV_DATABASECHANGELOG'
);
PREPARE create_flowable_changelog_stmt FROM @create_flowable_changelog_sql;
EXECUTE create_flowable_changelog_stmt;
DEALLOCATE PREPARE create_flowable_changelog_stmt;

SET @create_flowable_changelog_lock_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE TABLE `FLW_EV_DATABASECHANGELOGLOCK` (`ID` INT NOT NULL, `LOCKED` TINYINT NOT NULL, `LOCKGRANTED` DATETIME DEFAULT NULL, `LOCKEDBY` VARCHAR(255) COLLATE utf8mb3_bin DEFAULT NULL, PRIMARY KEY (`ID`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin',
        'DO 0'
    )
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'FLW_EV_DATABASECHANGELOGLOCK'
);
PREPARE create_flowable_changelog_lock_stmt FROM @create_flowable_changelog_lock_sql;
EXECUTE create_flowable_changelog_lock_stmt;
DEALLOCATE PREPARE create_flowable_changelog_lock_stmt;

INSERT INTO `FLW_EV_DATABASECHANGELOGLOCK` (`ID`, `LOCKED`, `LOCKGRANTED`, `LOCKEDBY`)
SELECT 1, 0, NULL, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `FLW_EV_DATABASECHANGELOGLOCK` WHERE `ID` = 1
);
