ALTER TABLE `xiaozhi`.`sys_device` 
ADD COLUMN `location` varchar(255) DEFAULT NULL COMMENT '地理位置' AFTER `ip`;