ALTER TABLE `Facet_SmsEntry` CHANGE COLUMN `attachmentMimeTypes` `attachmentMimeTypes` VARCHAR(1024) CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_unicode_ci' NULL DEFAULT NULL  , CHANGE COLUMN `attachmentNames` `attachmentNames` LONGTEXT CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_unicode_ci' NULL DEFAULT NULL ;