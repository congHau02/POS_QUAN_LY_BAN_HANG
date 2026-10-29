-- Flyway Schema Migration V1: Initial Schema
-- Billing Application - POS Management System

CREATE TABLE IF NOT EXISTS `tbl_users` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` VARCHAR(255) NULL,
  `name` VARCHAR(255) NULL,
  `email` VARCHAR(255) NULL,
  `password` VARCHAR(255) NULL,
  `role` VARCHAR(255) NULL,
  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_user_id` (`user_id`),
  UNIQUE KEY `uk_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `refresh_tokens` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `token_hash` VARCHAR(128) NOT NULL,
  `user_email` VARCHAR(255) NOT NULL,
  `family_id` VARCHAR(36) NOT NULL,
  `issued_at` TIMESTAMP NOT NULL,
  `expires_at` TIMESTAMP NOT NULL,
  `revoked_at` TIMESTAMP NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_token_hash` (`token_hash`),
  KEY `idx_refresh_token_family` (`family_id`),
  KEY `idx_refresh_token_user` (`user_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tbl_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `category_id` VARCHAR(255) NULL,
  `name` VARCHAR(255) NULL,
  `description` VARCHAR(255) NULL,
  `bg_color` VARCHAR(255) NULL,
  `img_url` VARCHAR(255) NULL,
  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_category_id` (`category_id`),
  UNIQUE KEY `uk_category_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tbl_items` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `item_id` VARCHAR(255) NULL,
  `name` VARCHAR(255) NULL,
  `description` VARCHAR(255) NULL,
  `price` DECIMAL(38,2) NULL,
  `category_id` BIGINT NOT NULL,
  `img_url` VARCHAR(255) NULL,
  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_item_id` (`item_id`),
  KEY `fk_item_category` (`category_id`),
  CONSTRAINT `fk_item_category` FOREIGN KEY (`category_id`) REFERENCES `tbl_category` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tbl_variants` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `variant_id` VARCHAR(255) NOT NULL,
  `sku` VARCHAR(255) NOT NULL,
  `base_price` DECIMAL(38,2) NOT NULL,
  `attributes` TEXT NULL,
  `cached_stock_quantity` INT NOT NULL DEFAULT 0,
  `item_id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_variant_variant_id` (`variant_id`),
  UNIQUE KEY `uk_variant_sku` (`sku`),
  KEY `fk_variant_item` (`item_id`),
  CONSTRAINT `fk_variant_item` FOREIGN KEY (`item_id`) REFERENCES `tbl_items` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tbl_modifier_groups` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `group_id` VARCHAR(255) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `description` VARCHAR(255) NULL,
  `min_selections` INT NOT NULL DEFAULT 0,
  `max_selections` INT NOT NULL DEFAULT 1,
  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_modifier_group_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tbl_modifiers` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `modifier_id` VARCHAR(255) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `price_adjustment` DECIMAL(38,2) NOT NULL DEFAULT 0.00,
  `group_id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_modifier_modifier_id` (`modifier_id`),
  KEY `fk_modifier_group` (`group_id`),
  CONSTRAINT `fk_modifier_group` FOREIGN KEY (`group_id`) REFERENCES `tbl_modifier_groups` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tbl_item_modifier_groups` (
  `item_id` BIGINT NOT NULL,
  `modifier_group_id` BIGINT NOT NULL,
  PRIMARY KEY (`item_id`, `modifier_group_id`),
  KEY `fk_img_group` (`modifier_group_id`),
  CONSTRAINT `fk_img_item` FOREIGN KEY (`item_id`) REFERENCES `tbl_items` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_img_group` FOREIGN KEY (`modifier_group_id`) REFERENCES `tbl_modifier_groups` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tbl_customers` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `customer_id` VARCHAR(255) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `phone_number` VARCHAR(255) NOT NULL,
  `email` VARCHAR(255) NULL,
  `total_spent` DOUBLE NOT NULL DEFAULT 0.0,
  `order_count` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_customer_id` (`customer_id`),
  UNIQUE KEY `uk_customer_phone_number` (`phone_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tbl_promotions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `promotion_id` VARCHAR(255) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `description` VARCHAR(255) NULL,
  `type` VARCHAR(255) NOT NULL,
  `code` VARCHAR(255) NULL,
  `discount_type` VARCHAR(255) NULL,
  `discount_value` DECIMAL(38,2) NOT NULL DEFAULT 0.00,
  `max_discount_amount` DECIMAL(38,2) NULL,
  `min_order_amount` DECIMAL(38,2) NOT NULL DEFAULT 0.00,
  `start_date` DATE NULL,
  `end_date` DATE NULL,
  `start_time` TIME NULL,
  `end_time` TIME NULL,
  `days_of_week` VARCHAR(255) NULL,
  `buy_variant_id` VARCHAR(255) NULL,
  `get_variant_id` VARCHAR(255) NULL,
  `bogo_discount_percent` DECIMAL(38,2) DEFAULT 100.00,
  `usage_limit` INT NULL,
  `times_used` INT NOT NULL DEFAULT 0,
  `is_active` BIT(1) NOT NULL DEFAULT b'1',
  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_promotion_promotion_id` (`promotion_id`),
  UNIQUE KEY `uk_promotion_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tbl_orders` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_code` VARCHAR(255) NOT NULL COMMENT 'Domain internal order code (mapped from OrderEntity.orderId)',
  `customer_id` VARCHAR(255) NULL,
  `customer_name` VARCHAR(255) NULL,
  `phone_number` VARCHAR(255) NULL,
  `subtotal` DOUBLE NULL,
  `discount_amount` DOUBLE DEFAULT 0.0,
  `tax` DOUBLE NULL,
  `grand_total` DOUBLE NULL,
  `promotion_id` VARCHAR(255) NULL,
  `promotion_name` VARCHAR(255) NULL,
  `created_at` DATETIME(6) NULL,
  `payment_method` VARCHAR(255) NULL,
  `order_id` VARCHAR(255) NULL COMMENT 'External PayOS gateway order ID (mapped from PaymentDetails.orderId)',
  `status` TINYINT NULL COMMENT 'Payment status ordinal: 0=PENDING, 1=COMPLETED, 2=FAILED, 3=CANCELLED',
  `payment_link_id` VARCHAR(255) NULL,
  `checkout_url` VARCHAR(1000) NULL,
  `qr_code` TEXT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_order_code` (`order_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tbl_order_items` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT NULL,
  `item_id` VARCHAR(255) NULL,
  `variant_id` VARCHAR(255) NULL,
  `name` VARCHAR(255) NULL,
  `base_price` DOUBLE NULL,
  `price` DOUBLE NULL,
  `quantity` INT NULL,
  `selected_modifiers` TEXT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_order_item_order` (`order_id`),
  CONSTRAINT `fk_order_item_order` FOREIGN KEY (`order_id`) REFERENCES `tbl_orders` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tbl_inventory_transactions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `transaction_id` VARCHAR(255) NOT NULL,
  `variant_id` BIGINT NOT NULL,
  `transaction_type` VARCHAR(255) NOT NULL,
  `quantity` INT NOT NULL,
  `reference_id` VARCHAR(255) NULL,
  `note` VARCHAR(255) NULL,
  `created_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inv_trans_id` (`transaction_id`),
  KEY `fk_inventory_variant` (`variant_id`),
  CONSTRAINT `fk_inventory_variant` FOREIGN KEY (`variant_id`) REFERENCES `tbl_variants` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tbl_activity_logs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `log_id` VARCHAR(255) NOT NULL,
  `user_email` VARCHAR(255) NOT NULL,
  `action` VARCHAR(255) NOT NULL,
  `entity_type` VARCHAR(255) NULL,
  `entity_id` VARCHAR(255) NULL,
  `description` VARCHAR(1000) NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_log_id` (`log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
