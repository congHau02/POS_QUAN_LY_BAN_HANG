-- =====================================================================
-- DỮ LIỆU MẪU QUÁN NƯỚC GIẢI KHÁT (BEVERAGE POS SAMPLE DATA)
-- File này độc lập hoàn toàn với Flyway schema version migrations.
-- Mục đích: Khởi tạo thực đơn 20 món nước, biến thể Size M/L, 
--           Topping, Mức đường/đá, Tồn kho 100 ly/SKU, Khách hàng CRM, 
--           Mã giảm giá và Đơn hàng mẫu phục vụ bán hàng POS & Dashboard.
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- 1. DỌN DẸP DỮ LIỆU CŨ (GIỮ NGUYÊN tbl_users VÀ refresh_tokens)
DELETE FROM `tbl_order_items`;
DELETE FROM `tbl_orders`;
DELETE FROM `tbl_inventory_transactions`;
DELETE FROM `tbl_item_modifier_groups`;
DELETE FROM `tbl_modifiers`;
DELETE FROM `tbl_modifier_groups`;
DELETE FROM `tbl_variants`;
DELETE FROM `tbl_items`;
DELETE FROM `tbl_category`;
DELETE FROM `tbl_customers`;
DELETE FROM `tbl_promotions`;
DELETE FROM `tbl_activity_logs`;

-- Reset auto-increment
ALTER TABLE `tbl_category` AUTO_INCREMENT = 1;
ALTER TABLE `tbl_items` AUTO_INCREMENT = 1;
ALTER TABLE `tbl_variants` AUTO_INCREMENT = 1;
ALTER TABLE `tbl_modifier_groups` AUTO_INCREMENT = 1;
ALTER TABLE `tbl_modifiers` AUTO_INCREMENT = 1;
ALTER TABLE `tbl_customers` AUTO_INCREMENT = 1;
ALTER TABLE `tbl_promotions` AUTO_INCREMENT = 1;
ALTER TABLE `tbl_orders` AUTO_INCREMENT = 1;
ALTER TABLE `tbl_order_items` AUTO_INCREMENT = 1;
ALTER TABLE `tbl_inventory_transactions` AUTO_INCREMENT = 1;
ALTER TABLE `tbl_activity_logs` AUTO_INCREMENT = 1;

-- 2. KHỞI TẠO 5 DANH MỤC ĐỒ UỐNG CHUẨN
INSERT INTO `tbl_category` (`id`, `category_id`, `name`, `description`, `bg_color`, `img_url`, `created_at`, `updated_at`) VALUES
(1, 'cat-coffee-01', 'Cà phê & Espresso', 'Cà phê nguyên chất pha phin truyền thống và cà phê máy phong cách Ý', '#78350f', 'https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),
(2, 'cat-fruittea-02', 'Trà trái cây & Nhiệt đới', 'Thức uống thanh nhiệt mát lạnh kết hợp từ trà hoa quả nhiệt đới tươi ngon', '#ea580c', 'https://images.unsplash.com/photo-1556881286-fc6915169721?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),
(3, 'cat-milktea-03', 'Trà sữa & Macchiato', 'Trà sữa thơm ngon béo ngậy phối cùng trân châu và lớp Milk Foam trứ danh', '#d97706', 'https://images.unsplash.com/photo-1589396575653-c09c794ff6a6?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),
(4, 'cat-juice-04', 'Sinh tố & Nước ép tươi', 'Sinh tố sánh mịn và nước ép 100% hoa quả tươi nguyên chất giàu dinh dưỡng', '#16a34a', 'https://images.unsplash.com/photo-1613478223719-2ab802602423?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),
(5, 'cat-iceblended-05', 'Đá xay & Đóng chai', 'Thức uống đá xay mát lạnh sảng khoái và nước ngọt, nước khoáng đóng lon/chai', '#0284c7', 'https://images.unsplash.com/photo-1572490122747-3968b75cc699?w=400&auto=format&fit=crop&q=80', NOW(), NOW());

-- 3. KHỞI TẠO 3 NHÓM MODIFIER (TOPPING, ĐƯỜNG, ĐÁ)
INSERT INTO `tbl_modifier_groups` (`id`, `group_id`, `name`, `description`, `min_selections`, `max_selections`, `created_at`, `updated_at`) VALUES
(1, 'grp-topping-01', 'Topping thêm', 'Thêm trân châu, thạch, kem cheese vào đồ uống', 0, 4, NOW(), NOW()),
(2, 'grp-sugar-02', 'Mức đường', 'Tùy chỉnh độ ngọt theo khẩu vị', 1, 1, NOW(), NOW()),
(3, 'grp-ice-03', 'Lượng đá', 'Tùy chỉnh độ lạnh của ly nước', 1, 1, NOW(), NOW());

-- 4. KHỞI TẠO CÁC LỰA CHỌN MODIFIERS CHI TIẾT
INSERT INTO `tbl_modifiers` (`id`, `modifier_id`, `name`, `price_adjustment`, `group_id`, `created_at`, `updated_at`) VALUES
-- Topping (có phí)
(1, 'mod-top-blackpearl', 'Trân châu đen dẻo', 5000.00, 1, NOW(), NOW()),
(2, 'mod-top-whitepearl', 'Trân châu trắng 3Q giòn', 6000.00, 1, NOW(), NOW()),
(3, 'mod-top-coconutjelly', 'Thạch dừa lá dứa', 5000.00, 1, NOW(), NOW()),
(4, 'mod-top-cheeseform', 'Kem Cheese hoàng kim béo ngậy', 10000.00, 1, NOW(), NOW()),
(5, 'mod-top-eggpudding', 'Pudding trứng mềm mịn', 7000.00, 1, NOW(), NOW()),
(6, 'mod-top-lotusseed', 'Hạt sen Huế bùi ngọt', 8000.00, 1, NOW(), NOW()),
-- Mức đường (0đ)
(7, 'mod-sug-100', '100% đường (Tiêu chuẩn)', 0.00, 2, NOW(), NOW()),
(8, 'mod-sug-70', '70% đường (Vừa ngọt)', 0.00, 2, NOW(), NOW()),
(9, 'mod-sug-50', '50% đường (Ít ngọt)', 0.00, 2, NOW(), NOW()),
(10, 'mod-sug-30', '30% đường (Rất ít ngọt)', 0.00, 2, NOW(), NOW()),
(11, 'mod-sug-0', '0% đường (Không đường)', 0.00, 2, NOW(), NOW()),
-- Lượng đá (0đ)
(12, 'mod-ice-100', '100% đá (Tiêu chuẩn)', 0.00, 3, NOW(), NOW()),
(13, 'mod-ice-50', '50% đá (Ít đá)', 0.00, 3, NOW(), NOW()),
(14, 'mod-ice-none', 'Không đá', 0.00, 3, NOW(), NOW()),
(15, 'mod-ice-hot', 'Uống nóng', 0.00, 3, NOW(), NOW());

-- 5. KHỞI TẠO 20 MÓN NƯỚC GIẢI KHÁT (ITEMS)
INSERT INTO `tbl_items` (`id`, `item_id`, `name`, `description`, `price`, `category_id`, `img_url`, `created_at`, `updated_at`) VALUES
-- Nhóm 1: Cà phê (category_id = 1)
(1, 'item-cf-suada', 'Cà phê sữa đá Sài Gòn', 'Cà phê Robusta Đắk Lắk pha phin truyền thống hòa quyện cùng sữa đặc béo ngậy', 25000.00, 1, 'https://images.unsplash.com/photo-1517701550927-30cf4ba1dba5?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),
(2, 'item-cf-denda', 'Cà phê đen đá phin', 'Vị đắng đậm đà nguyên bản từ hạt cà phê rang mộc Tây Nguyên', 22000.00, 1, 'https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),
(3, 'item-cf-bacxiu', 'Bạc xỉu ba tầng', 'Sự kết hợp hoàn hảo giữa sữa tươi, sữa đặc và một chút cà phê nồng nàn', 29000.00, 1, 'https://images.unsplash.com/photo-1541167760496-1628856ab772?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),
(4, 'item-cf-muoi', 'Cà phê muối béo ngậy', 'Lớp kem muối mằn mặn béo ngậy phủ trên cốt cà phê phin đậm đà xứ Huế', 32000.00, 1, 'https://images.unsplash.com/photo-1579992357154-faf4bde95b3d?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),

-- Nhóm 2: Trà trái cây (category_id = 2)
(5, 'item-tea-daocamsa', 'Trà đào cam sả thanh mát', 'Vị trà đào thơm lừng kết hợp lát cam vàng mọng nước và hương sả nồng nàn', 35000.00, 2, 'https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),
(6, 'item-tea-vaihatsen', 'Trà vải hạt sen', 'Hương sen thanh tao hòa cùng vị ngọt lịm của từng múi vải thiều ngâm giòn', 39000.00, 2, 'https://images.unsplash.com/photo-1576092768241-dec231879fc3?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),
(7, 'item-tea-oihong', 'Trà ổi hồng hạt chia', 'Màu hồng tự nhiên quyến rũ, vị ổi thơm mát kết hợp hạt chia bổ dưỡng', 35000.00, 2, 'https://images.unsplash.com/photo-1595981267035-7b04ca84a82d?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),
(8, 'item-tea-chanhleo', 'Trà chanh leo kim quất', 'Cực phẩm giải nhiệt mùa nắng với vị chua thanh sảng khoái kích thích vị giác', 30000.00, 2, 'https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),

-- Nhóm 3: Trà sữa & Macchiato (category_id = 3)
(9, 'item-milk-3ae', 'Trà sữa truyền thống ba anh em', 'Trà đen thơm ngát sữa béo, đầy đặn trân châu đen, thạch dừa và bánh pudding', 38000.00, 3, 'https://images.unsplash.com/photo-1589396575653-c09c794ff6a6?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),
(10, 'item-milk-olongnuong', 'Trà sữa Ô long nướng', 'Hương trà ô long sao nướng đậm khói hòa quyện sữa tươi thanh ngọt cuốn hút', 40000.00, 3, 'https://images.unsplash.com/photo-1571934811356-5cc061b6821f?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),
(11, 'item-milk-thaidomacchiato', 'Trà Thái đỏ Macchiato', 'Trà Thái đỏ truyền thống phủ lớp kem cheese Macchiato mềm mượt thơm ngậy', 36000.00, 3, 'https://images.unsplash.com/photo-1536256263959-770b48d82b0a?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),
(12, 'item-milk-matchauji', 'Trà sữa Matcha Uji Nhật Bản', 'Bột matcha Uji thượng hạng hòa quyện sữa tươi tạo hương vị thanh đắng ngọt dịu', 42000.00, 3, 'https://images.unsplash.com/photo-1536256263959-770b48d82b0a?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),

-- Nhóm 4: Sinh tố & Nước ép (category_id = 4)
(13, 'item-juice-bodua', 'Sinh tố bơ dừa sáp dẻo', 'Bơ sáp loại một xay nhuyễn cùng sữa đặc và nước cốt dừa tươi thơm béo', 45000.00, 4, 'https://images.unsplash.com/photo-1603569283847-aa295f0d016a?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),
(14, 'item-juice-xoai', 'Sinh tố xoài cát nhiệt đới', 'Xoài cát chín vàng tự nhiên xay cùng sữa chua mát lạnh giàu vitamin', 40000.00, 4, 'https://images.unsplash.com/photo-1546173159-315724a31696?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),
(15, 'item-juice-camnguyenchat', 'Nước ép cam sành nguyên chất', 'Cam sành tươi mọng nước ép nguyên chất không pha đường giải nhiệt tuyệt vời', 35000.00, 4, 'https://images.unsplash.com/photo-1613478223719-2ab802602423?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),
(16, 'item-juice-taocarot', 'Nước ép táo cà rốt detox', 'Công thức nước ép thanh lọc cơ thể tươi mát từ táo đỏ Mỹ và cà rốt giòn ngọt', 38000.00, 4, 'https://images.unsplash.com/photo-1595981267035-7b04ca84a82d?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),

-- Nhóm 5: Đá xay & Đóng chai (category_id = 5)
(17, 'item-ice-caramel', 'Cà phê Caramel đá xay', 'Espresso đậm vị xay cùng sốt caramel cao cấp, phủ ngọn kem tươi béo ngậy', 48000.00, 5, 'https://images.unsplash.com/photo-1572490122747-3968b75cc699?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),
(18, 'item-ice-matchacookie', 'Matcha Cookie đá xay', 'Matcha đá xay hòa cùng bánh quy oreo vụn giòn rụm thơm lừng', 48000.00, 5, 'https://images.unsplash.com/photo-1577805947697-89e18249d767?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),
(19, 'item-bot-lavie', 'Nước khoáng Lavie 500ml', 'Nước khoáng thiên nhiên đóng chai tiện lợi bổ sung khoáng chất', 12000.00, 5, 'https://images.unsplash.com/photo-1616118132534-381148898bb4?w=400&auto=format&fit=crop&q=80', NOW(), NOW()),
(20, 'item-bot-redbull', 'Nước tăng lực Red Bull lon', 'Nước tăng lực lon vàng đem lại năng lượng tỉnh táo tức thì', 20000.00, 5, 'https://images.unsplash.com/photo-1622543925917-763c34d1a86e?w=400&auto=format&fit=crop&q=80', NOW(), NOW());

-- 6. GÁN ITEM VỚI MODIFIER GROUPS (tbl_item_modifier_groups)
-- Cà phê (items 1..4): Đường (2), Đá (3), Topping (1)
INSERT INTO `tbl_item_modifier_groups` (`item_id`, `modifier_group_id`) VALUES
(1, 1), (1, 2), (1, 3),
(2, 2), (2, 3),
(3, 1), (3, 2), (3, 3),
(4, 1), (4, 2), (4, 3);

-- Trà trái cây (items 5..8): Topping (1), Đường (2), Đá (3)
INSERT INTO `tbl_item_modifier_groups` (`item_id`, `modifier_group_id`) VALUES
(5, 1), (5, 2), (5, 3),
(6, 1), (6, 2), (6, 3),
(7, 1), (7, 2), (7, 3),
(8, 1), (8, 2), (8, 3);

-- Trà sữa (items 9..12): Topping (1), Đường (2), Đá (3)
INSERT INTO `tbl_item_modifier_groups` (`item_id`, `modifier_group_id`) VALUES
(9, 1), (9, 2), (9, 3),
(10, 1), (10, 2), (10, 3),
(11, 1), (11, 2), (11, 3),
(12, 1), (12, 2), (12, 3);

-- Sinh tố & Nước ép (items 13..16): Đường (2), Đá (3)
INSERT INTO `tbl_item_modifier_groups` (`item_id`, `modifier_group_id`) VALUES
(13, 2), (13, 3),
(14, 2), (14, 3),
(15, 2), (15, 3),
(16, 2), (16, 3);

-- Đá xay (items 17..18): Topping (1)
INSERT INTO `tbl_item_modifier_groups` (`item_id`, `modifier_group_id`) VALUES
(17, 1),
(18, 1);

-- 7. KHỞI TẠO BIẾN THỂ (VARIANTS / SIZES) VÀ TỒN KHO 100 LY/SKU
INSERT INTO `tbl_variants` (`id`, `variant_id`, `sku`, `base_price`, `attributes`, `cached_stock_quantity`, `item_id`, `created_at`, `updated_at`) VALUES
-- Item 1: Cà phê sữa đá
(1, 'var-cf-01-m', 'CF-SUADA-M', 25000.00, '{"Size":"Size M"}', 100, 1, NOW(), NOW()),
(2, 'var-cf-01-l', 'CF-SUADA-L', 31000.00, '{"Size":"Size L"}', 100, 1, NOW(), NOW()),
-- Item 2: Cà phê đen đá
(3, 'var-cf-02-m', 'CF-DENDA-M', 22000.00, '{"Size":"Size M"}', 100, 2, NOW(), NOW()),
(4, 'var-cf-02-l', 'CF-DENDA-L', 28000.00, '{"Size":"Size L"}', 100, 2, NOW(), NOW()),
-- Item 3: Bạc xỉu ba tầng
(5, 'var-cf-03-m', 'CF-BACXIU-M', 29000.00, '{"Size":"Size M"}', 100, 3, NOW(), NOW()),
(6, 'var-cf-03-l', 'CF-BACXIU-L', 35000.00, '{"Size":"Size L"}', 100, 3, NOW(), NOW()),
-- Item 4: Cà phê muối
(7, 'var-cf-04-m', 'CF-MUOI-M', 32000.00, '{"Size":"Size M"}', 100, 4, NOW(), NOW()),
(8, 'var-cf-04-l', 'CF-MUOI-L', 38000.00, '{"Size":"Size L"}', 100, 4, NOW(), NOW()),
-- Item 5: Trà đào cam sả
(9, 'var-tea-05-m', 'TEA-DAOCAMSA-M', 35000.00, '{"Size":"Size M"}', 100, 5, NOW(), NOW()),
(10, 'var-tea-05-l', 'TEA-DAOCAMSA-L', 42000.00, '{"Size":"Size L"}', 100, 5, NOW(), NOW()),
-- Item 6: Trà vải hạt sen
(11, 'var-tea-06-m', 'TEA-VAIHATSEN-M', 39000.00, '{"Size":"Size M"}', 100, 6, NOW(), NOW()),
(12, 'var-tea-06-l', 'TEA-VAIHATSEN-L', 46000.00, '{"Size":"Size L"}', 100, 6, NOW(), NOW()),
-- Item 7: Trà ổi hồng
(13, 'var-tea-07-m', 'TEA-OIHONG-M', 35000.00, '{"Size":"Size M"}', 100, 7, NOW(), NOW()),
(14, 'var-tea-07-l', 'TEA-OIHONG-L', 42000.00, '{"Size":"Size L"}', 100, 7, NOW(), NOW()),
-- Item 8: Trà chanh leo kim quất
(15, 'var-tea-08-m', 'TEA-CHANHLEO-M', 30000.00, '{"Size":"Size M"}', 100, 8, NOW(), NOW()),
(16, 'var-tea-08-l', 'TEA-CHANHLEO-L', 36000.00, '{"Size":"Size L"}', 100, 8, NOW(), NOW()),
-- Item 9: Trà sữa ba anh em
(17, 'var-milk-09-m', 'MILK-3AE-M', 38000.00, '{"Size":"Size M"}', 100, 9, NOW(), NOW()),
(18, 'var-milk-09-l', 'MILK-3AE-L', 46000.00, '{"Size":"Size L"}', 100, 9, NOW(), NOW()),
-- Item 10: Trà sữa Ô long nướng
(19, 'var-milk-10-m', 'MILK-OLONG-M', 40000.00, '{"Size":"Size M"}', 100, 10, NOW(), NOW()),
(20, 'var-milk-10-l', 'MILK-OLONG-L', 48000.00, '{"Size":"Size L"}', 100, 10, NOW(), NOW()),
-- Item 11: Trà Thái đỏ Macchiato
(21, 'var-milk-11-m', 'MILK-THAIDO-M', 36000.00, '{"Size":"Size M"}', 100, 11, NOW(), NOW()),
(22, 'var-milk-11-l', 'MILK-THAIDO-L', 43000.00, '{"Size":"Size L"}', 100, 11, NOW(), NOW()),
-- Item 12: Trà sữa Matcha Uji
(23, 'var-milk-12-m', 'MILK-MATCHA-M', 42000.00, '{"Size":"Size M"}', 100, 12, NOW(), NOW()),
(24, 'var-milk-12-l', 'MILK-MATCHA-L', 50000.00, '{"Size":"Size L"}', 100, 12, NOW(), NOW()),
-- Item 13: Sinh tố bơ dừa sáp
(25, 'var-juice-13-m', 'JUICE-BODUA-M', 45000.00, '{"Size":"Size M"}', 100, 13, NOW(), NOW()),
(26, 'var-juice-13-l', 'JUICE-BODUA-L', 52000.00, '{"Size":"Size L"}', 100, 13, NOW(), NOW()),
-- Item 14: Sinh tố xoài cát
(27, 'var-juice-14-m', 'JUICE-XOAI-M', 40000.00, '{"Size":"Size M"}', 100, 14, NOW(), NOW()),
(28, 'var-juice-14-l', 'JUICE-XOAI-L', 47000.00, '{"Size":"Size L"}', 100, 14, NOW(), NOW()),
-- Item 15: Nước ép cam sành
(29, 'var-juice-15-m', 'JUICE-CAM-M', 35000.00, '{"Size":"Size M"}', 100, 15, NOW(), NOW()),
(30, 'var-juice-15-l', 'JUICE-CAM-L', 42000.00, '{"Size":"Size L"}', 100, 15, NOW(), NOW()),
-- Item 16: Nước ép táo cà rốt
(31, 'var-juice-16-m', 'JUICE-TAOCAROT-M', 38000.00, '{"Size":"Size M"}', 100, 16, NOW(), NOW()),
(32, 'var-juice-16-l', 'JUICE-TAOCAROT-L', 45000.00, '{"Size":"Size L"}', 100, 16, NOW(), NOW()),
-- Item 17: Cà phê Caramel đá xay
(33, 'var-ice-17-m', 'ICE-CARAMEL-M', 48000.00, '{"Size":"Size M"}', 100, 17, NOW(), NOW()),
(34, 'var-ice-17-l', 'ICE-CARAMEL-L', 55000.00, '{"Size":"Size L"}', 100, 17, NOW(), NOW()),
-- Item 18: Matcha Cookie đá xay
(35, 'var-ice-18-m', 'ICE-MATCHA-M', 48000.00, '{"Size":"Size M"}', 100, 18, NOW(), NOW()),
(36, 'var-ice-18-l', 'ICE-MATCHA-L', 55000.00, '{"Size":"Size L"}', 100, 18, NOW(), NOW()),
-- Item 19: Nước khoáng Lavie
(37, 'var-bot-19-m', 'BOT-LAVIE-500ML', 12000.00, '{"Size":"Size M (500ml)"}', 100, 19, NOW(), NOW()),
(38, 'var-bot-19-l', 'BOT-LAVIE-1500ML', 22000.00, '{"Size":"Size L (1.5L)"}', 100, 19, NOW(), NOW()),
-- Item 20: Nước tăng lực Red Bull
(39, 'var-bot-20-m', 'BOT-REDBULL-250ML', 20000.00, '{"Size":"Size M (Lon 250ml)"}', 100, 20, NOW(), NOW()),
(40, 'var-bot-20-l', 'BOT-REDBULL-330ML', 26000.00, '{"Size":"Size L (Lon 330ml)"}', 100, 20, NOW(), NOW());

-- 8. TẠO GIAO DỊCH SỔ CÁI NHẬP KHO BAN ĐẦU (tbl_inventory_transactions)
-- Nhập kho ban đầu 100 ly cho mỗi biến thể
INSERT INTO `tbl_inventory_transactions` (`id`, `transaction_id`, `variant_id`, `transaction_type`, `quantity`, `reference_id`, `note`, `created_at`)
SELECT 
  id AS `id`,
  CONCAT('tx-init-', id) AS `transaction_id`,
  id AS `variant_id`,
  'IN' AS `transaction_type`,
  100 AS `quantity`,
  'PO-INIT-BEVERAGE' AS `reference_id`,
  'Nhập tồn kho khai trương ban đầu cho quán' AS `note`,
  NOW() AS `created_at`
FROM `tbl_variants`;

-- 9. KHỞI TẠO 5 KHÁCH HÀNG THÂN THIẾT (CRM CUSTOMERS)
INSERT INTO `tbl_customers` (`id`, `customer_id`, `name`, `phone_number`, `email`, `total_spent`, `order_count`, `created_at`, `updated_at`) VALUES
(1, 'cust-001', 'Nguyễn Văn An', '0912345678', 'an.nguyen@gmail.com', 150000.0, 3, NOW(), NOW()),
(2, 'cust-002', 'Trần Thị Mai', '0987654321', 'mai.tran@gmail.com', 95000.0, 2, NOW(), NOW()),
(3, 'cust-003', 'Lê Hoàng Nam', '0903123456', 'nam.le@gmail.com', 45000.0, 1, NOW(), NOW()),
(4, 'cust-004', 'Phạm Thu Hà', '0938765432', 'ha.pham@gmail.com', 120000.0, 2, NOW(), NOW()),
(5, 'cust-005', 'Đỗ Minh Khang', '0977889900', 'khang.do@gmail.com', 0.0, 0, NOW(), NOW());

-- 10. KHỞI TẠO 3 CHƯƠNG TRÌNH KHUYẾN MÃI (PROMOTIONS)
INSERT INTO `tbl_promotions` (`id`, `promotion_id`, `name`, `description`, `type`, `code`, `discount_type`, `discount_value`, `max_discount_amount`, `min_order_amount`, `start_date`, `end_date`, `buy_variant_id`, `get_variant_id`, `bogo_discount_percent`, `usage_limit`, `times_used`, `is_active`, `created_at`, `updated_at`) VALUES
(1, 'prom-chaoban', 'Khai trương rộn ràng - Giảm 10%', 'Mã giảm giá 10% chào mừng khách hàng mới', 'COUPON', 'CHAOBAN', 'PERCENTAGE', 10.00, 50000.00, 0.00, '2026-01-01', '2026-12-31', NULL, NULL, 100.00, 500, 1, 1, NOW(), NOW()),
(2, 'prom-giam20k', 'Tiệc nước giải khát - Giảm ngay 20K', 'Giảm trực tiếp 20.000đ cho đơn hàng đồ uống từ 80.000đ trở lên', 'COUPON', 'GIAM20K', 'FIXED_AMOUNT', 20000.00, 20000.00, 80000.00, '2026-01-01', '2026-12-31', NULL, NULL, 100.00, 300, 0, 1, NOW(), NOW()),
(3, 'prom-bogo', 'Giờ vàng Mua 1 Tặng 1', 'Mua 1 ly trà đào cam sả tặng 1 ly trà chanh leo kim quất', 'BOGO', 'MUA1TANG1', NULL, 0.00, NULL, 0.00, '2026-01-01', '2026-12-31', 'var-tea-05-m', 'var-tea-08-m', 100.00, 100, 0, 1, NOW(), NOW());

-- 11. KHỞI TẠO ĐƠN HÀNG MẪU HÔM NAY (CHO DASHBOARD VÀ LỊCH SỬ ĐƠN HÀNG)
-- Đơn 1: Khách Nguyễn Văn An - 1 Cà phê sữa đá L + 1 Trà đào cam sả M (Thanh toán Tiền mặt)
INSERT INTO `tbl_orders` (`id`, `order_code`, `customer_id`, `customer_name`, `phone_number`, `subtotal`, `discount_amount`, `tax`, `grand_total`, `promotion_id`, `promotion_name`, `created_at`, `payment_method`, `order_id`, `status`) VALUES
(1, 'ORD2026092301', 'cust-001', 'Nguyễn Văn An', '0912345678', 66000.0, 0.0, 0.0, 66000.0, NULL, NULL, NOW(), 'CASH', 'ORD2026092301', 1);

INSERT INTO `tbl_order_items` (`id`, `order_id`, `item_id`, `variant_id`, `name`, `base_price`, `price`, `quantity`, `selected_modifiers`) VALUES
(1, 1, 'item-cf-suada', 'var-cf-01-l', 'Cà phê sữa đá Sài Gòn (Size L)', 31000.0, 31000.0, 1, '[]'),
(2, 1, 'item-tea-daocamsa', 'var-tea-05-m', 'Trà đào cam sả thanh mát (Size M)', 35000.0, 35000.0, 1, '[]');

-- Đơn 2: Khách Trần Thị Mai - 2 Trà sữa Ô long nướng L + Topping trân châu đen (Thanh toán PayOS QR)
INSERT INTO `tbl_orders` (`id`, `order_code`, `customer_id`, `customer_name`, `phone_number`, `subtotal`, `discount_amount`, `tax`, `grand_total`, `promotion_id`, `promotion_name`, `created_at`, `payment_method`, `order_id`, `status`) VALUES
(2, 'ORD2026092302', 'cust-002', 'Trần Thị Mai', '0987654321', 106000.0, 10600.0, 0.0, 95400.0, 'prom-chaoban', 'Khai trương rộn ràng - Giảm 10%', NOW(), 'PAYOS', 'ORD2026092302', 1);

INSERT INTO `tbl_order_items` (`id`, `order_id`, `item_id`, `variant_id`, `name`, `base_price`, `price`, `quantity`, `selected_modifiers`) VALUES
(3, 2, 'item-milk-olongnuong', 'var-milk-10-l', 'Trà sữa Ô long nướng (Size L)', 48000.0, 53000.0, 2, '[{"modifierId":"mod-top-blackpearl","name":"Trân châu đen dẻo","priceAdjustment":5000.0}]');

-- Đơn 3: Khách Lê Hoàng Nam - 1 Sinh tố bơ dừa sáp M (Tiền mặt)
INSERT INTO `tbl_orders` (`id`, `order_code`, `customer_id`, `customer_name`, `phone_number`, `subtotal`, `discount_amount`, `tax`, `grand_total`, `promotion_id`, `promotion_name`, `created_at`, `payment_method`, `order_id`, `status`) VALUES
(3, 'ORD2026092303', 'cust-003', 'Lê Hoàng Nam', '0903123456', 45000.0, 0.0, 0.0, 45000.0, NULL, NULL, NOW(), 'CASH', 'ORD2026092303', 1);

INSERT INTO `tbl_order_items` (`id`, `order_id`, `item_id`, `variant_id`, `name`, `base_price`, `price`, `quantity`, `selected_modifiers`) VALUES
(4, 3, 'item-juice-bodua', 'var-juice-13-m', 'Sinh tố bơ dừa sáp dẻo (Size M)', 45000.0, 45000.0, 1, '[]');

-- Đơn 4: Khách vãng lai - 1 Cà phê Caramel đá xay M + 1 Nước khoáng Lavie (Tiền mặt)
INSERT INTO `tbl_orders` (`id`, `order_code`, `customer_id`, `customer_name`, `phone_number`, `subtotal`, `discount_amount`, `tax`, `grand_total`, `promotion_id`, `promotion_name`, `created_at`, `payment_method`, `order_id`, `status`) VALUES
(4, 'ORD2026092304', NULL, 'Khách vãng lai', '0900000000', 60000.0, 0.0, 0.0, 60000.0, NULL, NULL, NOW(), 'CASH', 'ORD2026092304', 1);

INSERT INTO `tbl_order_items` (`id`, `order_id`, `item_id`, `variant_id`, `name`, `base_price`, `price`, `quantity`, `selected_modifiers`) VALUES
(5, 4, 'item-ice-caramel', 'var-ice-17-m', 'Cà phê Caramel đá xay (Size M)', 48000.0, 48000.0, 1, '[]'),
(6, 4, 'item-bot-lavie', 'var-bot-19-m', 'Nước khoáng Lavie 500ml (Size M)', 12000.0, 12000.0, 1, '[]');

-- 12. KHỞI TẠO NHẬT KÝ HOẠT ĐỘNG BAN ĐẦU (tbl_activity_logs)
-- Lấy động email admin trong hệ thống, đảm bảo đúng chuẩn CONTEXT.md
SET @admin_email = (SELECT `email` FROM `tbl_users` WHERE `role` = 'ROLE_ADMIN' ORDER BY `id` ASC LIMIT 1);
SET @admin_email = IFNULL(@admin_email, 'admin@billingapp.com');

INSERT INTO `tbl_activity_logs` (`id`, `log_id`, `user_email`, `action`, `entity_type`, `entity_id`, `description`, `created_at`) VALUES
(1, 'act-init-01', @admin_email, 'CREATE', 'CATEGORY', '5-categories', 'Khởi tạo 5 danh mục đồ uống giải khát chuẩn', NOW()),
(2, 'act-init-02', @admin_email, 'CREATE', 'ITEM', '20-items', 'Thêm mới thực đơn 20 món nước giải khát', NOW()),
(3, 'act-init-03', @admin_email, 'CREATE', 'MODIFIER', '3-groups', 'Cấu hình các nhóm tùy chọn Topping, Mức đường, Lượng đá', NOW()),
(4, 'act-init-04', @admin_email, 'CREATE', 'INVENTORY', 'PO-INIT-BEVERAGE', 'Nhập tồn kho ban đầu 100 ly/SKU cho toàn bộ các biến thể', NOW()),
(5, 'act-init-05', @admin_email, 'CREATE', 'CUSTOMER', '5-customers', 'Khởi tạo danh sách 5 khách hàng thân thiết ban đầu', NOW()),
(6, 'act-init-06', @admin_email, 'CREATE', 'PROMOTION', 'prom-chaoban', 'Kích hoạt các chương trình khuyến mãi CHAOBAN, GIAM20K, MUA1TANG1', NOW()),
(7, 'act-init-07', @admin_email, 'CREATE', 'ORDER', 'ORD2026092301', 'Hoàn tất các đơn hàng bán lẻ khai trương mẫu trong ngày', NOW());

SET FOREIGN_KEY_CHECKS = 1;
