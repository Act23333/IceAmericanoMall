-- 一次性迁移：order:write → order:admin（batch 3 裁决，2026-10-18）
--
-- 为什么需要这个文件：Initialize.sql 是**全新安装**脚本。种子在 8304f976 里落的位是 `order:write`，
-- 后续把它改名为 `order:admin`（避免与买家侧 order:read 同名异义）—— 对**已装库**改 Initialize.sql 不生效，
-- 必须显式 UPDATE，否则会出现「代码注解找 order:admin、库里只有 order:write」⇒ 该位不存在 ⇒ 永久 403。
--
-- 本仓没有迁移目录（ADR-045：Initialize.sql 为唯一结构真相源、一次性装库）。本文件是**例外**，
-- 因为它修的是"已装库的数据行"而非常规结构演进；执行完即可删除。
--
-- 执行后请跑 scripts/verify-rbac-seed.sh 复核（期望 id 7..29 共 23 位 status=1）。

-- 1) 改名（幂等：只有旧名存在时才改）
UPDATE `sys_permission` SET `code` = 'order:admin' WHERE `code` = 'order:write';

-- 2) 复核：此时应只剩 order:admin，且 order:write 计数为 0
SELECT 'order:admin' AS expect, COUNT(*) AS cnt FROM `sys_permission` WHERE `code` = 'order:admin'
UNION ALL
SELECT 'order:write', COUNT(*) FROM `sys_permission` WHERE `code` = 'order:write';
