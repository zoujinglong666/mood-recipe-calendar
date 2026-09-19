-- 已部署数据库执行一次；会员道具 ID 创建后再补 platform_item_id。
UPDATE virtual_products
SET title = '锅仔会员 30 天',
    platform_item_id = 'GZHY_30D',
    description = '30 天内畅享锅仔管饭、AI 私人菜单与月度画册高清导出。',
    price_fen = 990,
    entitlement_code = 'MEMBER',
    valid_days = 30,
    active = 1
WHERE sku = 'GUOZAI_MEMBER_30D';
