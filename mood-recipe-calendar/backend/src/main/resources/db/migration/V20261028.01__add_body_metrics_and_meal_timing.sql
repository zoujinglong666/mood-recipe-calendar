-- [2026-10-28] EER 营养目标输入 + 问卷补充字段（user_food_preferences）
-- 依据：NASEM 2023 成人 EER 需 性别/年龄/身高/体重/活动等级/力量训练 六项输入；
-- body metrics 全部可空——未填时目标计算返回 NEEDS_INPUT，不猜测。
ALTER TABLE user_food_preferences
    ADD COLUMN gender VARCHAR(8) NULL COMMENT '生理性别：MALE/FEMALE（仅决定EER方程系数组）',
    ADD COLUMN age INT NULL COMMENT '年龄（岁），EER 支持 19-64',
    ADD COLUMN height_cm DECIMAL(5,1) NULL COMMENT '身高 cm',
    ADD COLUMN weight_kg DECIMAL(5,1) NULL COMMENT '体重 kg',
    ADD COLUMN activity_level VARCHAR(16) NULL COMMENT '活动等级：INACTIVE/LOW_ACTIVE/ACTIVE/VERY_ACTIVE',
    ADD COLUMN resistance_training TINYINT(1) NULL COMMENT '是否规律力量训练（决定蛋白质范围系数组）',
    ADD COLUMN meal_timing VARCHAR(32) NULL COMMENT '进食节奏：REGULAR/NO_BREAKFAST/LATE_NIGHT/INTERMITTENT';
