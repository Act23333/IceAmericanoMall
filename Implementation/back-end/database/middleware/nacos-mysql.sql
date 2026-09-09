-- ============================================
-- Nacos 2.4.0 MySQL 持久化表结构
-- 来源: https://github.com/alibaba/nacos/blob/2.4.0/distribution/conf/mysql-schema.sql
-- 执行方式: docker compose exec mysql mysql -u root -p icedamericano_mall < database/middleware/nacos-mysql.sql
-- ============================================

CREATE DATABASE IF NOT EXISTS `nacos_config` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `nacos_config`;

CREATE TABLE IF NOT EXISTS `config_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `data_id` varchar(255) NOT NULL,
  `group_id` varchar(128) DEFAULT NULL,
  `content` longtext NOT NULL,
  `md5` varchar(32) DEFAULT NULL,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `src_user` text,
  `src_ip` varchar(50) DEFAULT NULL,
  `app_name` varchar(128) DEFAULT NULL,
  `tenant_id` varchar(128) DEFAULT '',
  `c_desc` varchar(256) DEFAULT NULL,
  `c_use` varchar(64) DEFAULT NULL,
  `effect` varchar(64) DEFAULT NULL,
  `type` varchar(64) DEFAULT NULL,
  `c_schema` text,
  `encrypted_data_key` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfo_datagrouptenant` (`data_id`,`group_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `config_info_aggr` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `data_id` varchar(255) NOT NULL,
  `group_id` varchar(128) NOT NULL,
  `datum_id` varchar(255) NOT NULL,
  `content` longtext NOT NULL,
  `gmt_modified` datetime NOT NULL,
  `app_name` varchar(128) DEFAULT NULL,
  `tenant_id` varchar(128) DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfoaggr_datagrouptenantdatum` (`data_id`,`group_id`,`tenant_id`,`datum_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `config_info_beta` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `data_id` varchar(255) NOT NULL,
  `group_id` varchar(128) NOT NULL,
  `tenant_id` varchar(128) DEFAULT '',
  `app_name` varchar(128) DEFAULT NULL,
  `content` longtext NOT NULL,
  `beta_ips` varchar(1024) DEFAULT NULL,
  `md5` varchar(32) DEFAULT NULL,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `src_user` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfobeta_datagrouptenant` (`data_id`,`group_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `config_info_tag` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `data_id` varchar(255) NOT NULL,
  `group_id` varchar(128) NOT NULL,
  `tenant_id` varchar(128) DEFAULT '',
  `tag_id` varchar(128) NOT NULL,
  `app_name` varchar(128) DEFAULT NULL,
  `content` longtext NOT NULL,
  `md5` varchar(32) DEFAULT NULL,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `src_user` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfotag_datagrouptenanttag` (`data_id`,`group_id`,`tenant_id`,`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `config_tags_relation` (
  `id` bigint(20) NOT NULL,
  `tag_name` varchar(128) NOT NULL,
  `tag_type` varchar(64) DEFAULT NULL,
  `data_id` varchar(255) NOT NULL,
  `group_id` varchar(128) NOT NULL,
  `tenant_id` varchar(128) DEFAULT '',
  `nid` bigint(20) NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`nid`),
  UNIQUE KEY `uk_configtagrelation_configidtag` (`id`,`tag_name`,`tag_type`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `group_capacity` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `group_id` varchar(128) NOT NULL DEFAULT '',
  `quota` int(10) unsigned NOT NULL DEFAULT '0',
  `usage` int(10) unsigned NOT NULL DEFAULT '0',
  `max_size` int(10) unsigned NOT NULL DEFAULT '0',
  `max_aggr_count` int(10) unsigned NOT NULL DEFAULT '0',
  `max_aggr_size` int(10) unsigned NOT NULL DEFAULT '0',
  `max_history_count` int(10) unsigned NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `his_config_info` (
  `id` bigint(64) unsigned NOT NULL,
  `nid` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `data_id` varchar(255) NOT NULL,
  `group_id` varchar(128) NOT NULL,
  `app_name` varchar(128) DEFAULT NULL,
  `content` longtext NOT NULL,
  `md5` varchar(32) DEFAULT NULL,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `src_user` text,
  `src_ip` varchar(50) DEFAULT NULL,
  `op_type` char(10) DEFAULT NULL,
  `tenant_id` varchar(128) DEFAULT '',
  `encrypted_data_key` text,
  PRIMARY KEY (`nid`),
  KEY `idx_gmt_create` (`gmt_create`),
  KEY `idx_gmt_modified` (`gmt_modified`),
  KEY `idx_did` (`data_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tenant_capacity` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` varchar(128) NOT NULL DEFAULT '',
  `quota` int(10) unsigned NOT NULL DEFAULT '0',
  `usage` int(10) unsigned NOT NULL DEFAULT '0',
  `max_size` int(10) unsigned NOT NULL DEFAULT '0',
  `max_aggr_count` int(10) unsigned NOT NULL DEFAULT '0',
  `max_aggr_size` int(10) unsigned NOT NULL DEFAULT '0',
  `max_history_count` int(10) unsigned NOT NULL DEFAULT '0',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tenant_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `kp` varchar(128) NOT NULL,
  `tenant_id` varchar(128) DEFAULT '',
  `tenant_name` varchar(128) DEFAULT '',
  `tenant_desc` varchar(256) DEFAULT NULL,
  `create_source` varchar(32) DEFAULT NULL,
  `gmt_create` bigint(20) NOT NULL,
  `gmt_modified` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_info_kptenantid` (`kp`,`tenant_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `users` (
  `username` varchar(50) NOT NULL,
  `password` varchar(500) NOT NULL,
  `enabled` tinyint(1) NOT NULL,
  PRIMARY KEY (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `roles` (
  `username` varchar(50) NOT NULL,
  `role` varchar(50) NOT NULL,
  UNIQUE KEY `idx_user_role` (`username`,`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `permissions` (
  `role` varchar(50) NOT NULL,
  `resource` varchar(255) NOT NULL,
  `action` varchar(8) NOT NULL,
  UNIQUE KEY `uk_role_permission` (`role`,`resource`,`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
