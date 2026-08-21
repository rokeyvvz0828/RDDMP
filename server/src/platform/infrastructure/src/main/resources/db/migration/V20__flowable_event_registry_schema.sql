-- Flowable 7.0.1 event registry metadata schema.
-- Source: flowable-event-registry official Liquibase changelog.
create table FLW_EVENT_DEPLOYMENT (
    ID_ varchar(255) not null,
    NAME_ varchar(255),
    CATEGORY_ varchar(255),
    DEPLOY_TIME_ datetime(3),
    TENANT_ID_ varchar(255),
    PARENT_DEPLOYMENT_ID_ varchar(255),
    primary key (ID_)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

create table FLW_EVENT_RESOURCE (
    ID_ varchar(255) not null,
    NAME_ varchar(255),
    DEPLOYMENT_ID_ varchar(255),
    RESOURCE_BYTES_ longblob,
    primary key (ID_)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

create table FLW_EVENT_DEFINITION (
    ID_ varchar(255) not null,
    NAME_ varchar(255),
    VERSION_ integer,
    KEY_ varchar(255),
    CATEGORY_ varchar(255),
    DEPLOYMENT_ID_ varchar(255),
    TENANT_ID_ varchar(255),
    RESOURCE_NAME_ varchar(255),
    DESCRIPTION_ varchar(255),
    primary key (ID_)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

create unique index ACT_IDX_EVENT_DEF_UNIQ
    on FLW_EVENT_DEFINITION(KEY_, VERSION_, TENANT_ID_);

create table FLW_CHANNEL_DEFINITION (
    ID_ varchar(255) not null,
    NAME_ varchar(255),
    VERSION_ integer,
    KEY_ varchar(255),
    CATEGORY_ varchar(255),
    DEPLOYMENT_ID_ varchar(255),
    CREATE_TIME_ datetime(3),
    TENANT_ID_ varchar(255),
    RESOURCE_NAME_ varchar(255),
    DESCRIPTION_ varchar(255),
    TYPE_ varchar(255),
    IMPLEMENTATION_ varchar(255),
    primary key (ID_)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

create unique index ACT_IDX_CHANNEL_DEF_UNIQ
    on FLW_CHANNEL_DEFINITION(KEY_, VERSION_, TENANT_ID_);

-- Liquibase changelog tables maintained by the Flowable event registry engine.
-- Added during branch-integration migration governance so a fresh database can
-- build from scratch (V24 adds column comments to these tables).
create table FLW_EV_DATABASECHANGELOG (
    ID varchar(255) not null,
    AUTHOR varchar(255) not null,
    FILENAME varchar(255) not null,
    DATEEXECUTED datetime not null,
    ORDEREXECUTED integer not null,
    EXECTYPE varchar(10) not null,
    MD5SUM varchar(35),
    DESCRIPTION varchar(255),
    COMMENTS varchar(255),
    TAG varchar(255),
    LIQUIBASE varchar(20),
    CONTEXTS varchar(255),
    LABELS varchar(255),
    DEPLOYMENT_ID varchar(10),
    primary key (ID, AUTHOR, FILENAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

create table FLW_EV_DATABASECHANGELOGLOCK (
    ID integer not null,
    LOCKED bit not null,
    LOCKGRANTED datetime,
    LOCKEDBY varchar(255),
    primary key (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

insert into FLW_EV_DATABASECHANGELOGLOCK (ID, LOCKED) values (1, 0);
