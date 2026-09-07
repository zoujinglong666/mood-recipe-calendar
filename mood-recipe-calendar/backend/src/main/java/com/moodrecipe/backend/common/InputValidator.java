package com.moodrecipe.backend.common;

/**
 * 输入校验与清洗工具类。
 * 所有用户可控的字符串字段在写入数据库前必须经过校验。
 */
public final class InputValidator {

    private InputValidator() {}

    /** 昵称最大长度 */
    public static final int MAX_NICKNAME = 32;
    /** 菜名最大长度 */
    public static final int MAX_DISH_NAME = 50;
    /** 心情标签最大长度 */
    public static final int MAX_MOOD = 10;
    /** 备注最大长度 */
    public static final int MAX_NOTE = 500;
    /** 反馈内容最大长度 */
    public static final int MAX_FEEDBACK = 1000;
    /** 联系方式最大长度 */
    public static final int MAX_CONTACT = 100;

    /**
     * 校验字符串非空且在长度范围内，返回清洗后的值（去除首尾空白）。
     * @throws IllegalArgumentException 如果为空或超长
     */
    public static String requireNonEmpty(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " 长度不能超过 " + maxLength + " 字符");
        }
        return trimmed;
    }

    /**
     * 可选字符串：为空返回 null，否则校验长度并清洗。
     */
    public static String optional(String value, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException("输入长度不能超过 " + maxLength + " 字符");
        }
        return trimmed;
    }

    /**
     * 校验 openid 格式（非空、长度合理、只含安全字符）。
     */
    public static String requireOpenid(String openid) {
        if (openid == null || openid.trim().isEmpty()) {
            throw new IllegalArgumentException("openid 不能为空");
        }
        String trimmed = openid.trim();
        // 微信 openid 通常 28 位，mock_ 前缀的开发 openid 也允许
        if (trimmed.length() < 6 || trimmed.length() > 64) {
            throw new IllegalArgumentException("openid 格式无效");
        }
        // 只允许字母、数字、下划线、连字符
        if (!trimmed.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("openid 包含非法字符");
        }
        return trimmed;
    }

    /**
     * 校验日期格式 YYYY-MM-DD。
     */
    public static String requireDate(String date, String fieldName) {
        if (date == null || !date.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            throw new IllegalArgumentException(fieldName + " 格式应为 YYYY-MM-DD");
        }
        return date;
    }

    /**
     * 校验年份（4位数字）。
     */
    public static int requireYear(String yearStr) {
        if (yearStr == null || !yearStr.matches("^\\d{4}$")) {
            throw new IllegalArgumentException("年份格式应为 4 位数字");
        }
        int year = Integer.parseInt(yearStr);
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("年份范围应在 2000-2100 之间");
        }
        return year;
    }

    /**
     * 校验页码和每页数量，返回合理范围内的值。
     */
    public static int[] sanitizePagination(Integer page, Integer pageSize, int maxPageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int ps = (pageSize == null || pageSize < 1) ? 20 : Math.min(pageSize, maxPageSize);
        return new int[]{p, ps};
    }
}
