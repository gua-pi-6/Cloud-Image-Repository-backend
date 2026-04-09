-- 图片协同编辑基础表结构。
-- 设计思路：
-- 1. picture_collab_state 存“当前快照”，方便新连接快速同步；
-- 2. picture_collab_op_log 存“操作历史”，用于幂等去重、审计与排错。
--
-- 注意：
-- 当前业务代码已经扩展到裁剪框字段，若数据库尚未补字段，
-- 需要结合额外的 ALTER TABLE 脚本一起执行。

create table if not exists picture_collab_state
(
    pictureId   bigint                             not null comment '图片 id',
    revision    bigint   default 0                 not null comment '当前版本号',
    angle       double   default 0                 not null comment '当前旋转角度',
    scale       double   default 1                 not null comment '当前缩放比例',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    primary key (pictureId),
    index idx_updateTime (updateTime)
) comment '图片协同状态' collate = utf8mb4_unicode_ci;

create table if not exists picture_collab_op_log
(
    id             bigint auto_increment comment 'id' primary key,
    opId           varchar(64)                        not null comment '客户端操作唯一 id',
    pictureId      bigint                             not null comment '图片 id',
    userId         bigint                             not null comment '操作者 id',
    baseRevision   bigint                             not null comment '客户端操作基线版本',
    opType         varchar(32)                        not null comment '操作类型',
    opValue        double                             not null comment '操作值',
    serverRevision bigint                             not null comment '服务端应用后版本',
    resultAngle    double                             not null comment '操作应用后的角度',
    resultScale    double                             not null comment '操作应用后的缩放',
    createTime     datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime     datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_opId (opId),
    index idx_picture_revision (pictureId, serverRevision),
    index idx_userId (userId)
) comment '图片协同操作日志' collate = utf8mb4_unicode_ci;
