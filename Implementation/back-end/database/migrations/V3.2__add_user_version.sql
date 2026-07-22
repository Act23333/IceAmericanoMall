-- V3.2: User 表增加乐观锁版本号
ALTER TABLE `user` ADD COLUMN `version` INT DEFAULT 0 NOT NULL COMMENT '乐观锁版本号';
