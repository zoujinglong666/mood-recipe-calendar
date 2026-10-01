package com.moodrecipe.backend.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.fail;

class SqlSchemaContractTest {
    private static final Path ENTITY_DIR = Path.of("src/main/java/com/moodrecipe/backend/entity");
    private static final Path INIT_SQL = Path.of("sql/init.sql");

    @Test
    void initSqlContainsEveryPersistedEntityTableAndColumn() throws Exception {
        String sql = Files.readString(INIT_SQL);
        List<String> missing = new ArrayList<>();

        for (Class<?> entity : entityClasses()) {
            String table = tableName(entity);
            String body = createTableBody(sql, table);
            if (body == null) {
                missing.add("table " + table);
                continue;
            }
            for (Field field : entity.getDeclaredFields()) {
                if (!persisted(field)) continue;
                String column = columnName(field);
                String sqlType = columnType(body, column);
                if (sqlType == null) {
                    missing.add(table + "." + column);
                } else if (!compatible(field.getType(), sqlType)) {
                    missing.add(table + "." + column + " type " + field.getType().getSimpleName() + "/" + sqlType);
                }
            }
        }

        if (!missing.isEmpty()) fail("sql/init.sql 缺少实体结构: " + String.join(", ", missing));
    }

    private List<Class<?>> entityClasses() throws IOException, ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        try (var files = Files.list(ENTITY_DIR)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString().replaceFirst("\\.java$", "");
                Class<?> type = Class.forName("com.moodrecipe.backend.entity." + name);
                if (type.isAnnotationPresent(Entity.class)) classes.add(type);
            }
        }
        return classes;
    }

    private String tableName(Class<?> entity) {
        Table table = entity.getAnnotation(Table.class);
        return table != null && !table.name().isBlank() ? table.name() : snakeCase(entity.getSimpleName());
    }

    private boolean persisted(Field field) {
        return !Modifier.isStatic(field.getModifiers()) && !field.isAnnotationPresent(Transient.class);
    }

    private String columnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        return column != null && !column.name().isBlank() ? column.name() : snakeCase(field.getName());
    }

    private String snakeCase(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private String createTableBody(String sql, String table) {
        Matcher matcher = Pattern.compile("(?i)CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+`?"
                + Pattern.quote(table) + "`?\\s*\\(").matcher(sql);
        if (!matcher.find()) return null;
        int start = matcher.end();
        int depth = 1;
        for (int index = start; index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (current == '(') depth++;
            if (current == ')' && --depth == 0) return sql.substring(start, index);
        }
        return null;
    }

    private String columnType(String tableBody, String column) {
        Matcher matcher = Pattern.compile("(?im)(?:^|,)\\s*`?" + Pattern.quote(column)
                + "`?\\s+(BIGINT|INT|TINYINT|VARCHAR|TEXT|DATE|DATETIME|DECIMAL|DOUBLE|BOOLEAN)\\b")
                .matcher(tableBody);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : null;
    }

    private boolean compatible(Class<?> javaType, String sqlType) {
        if (javaType == String.class) return sqlType.equals("VARCHAR") || sqlType.equals("TEXT");
        if (javaType == Long.class || javaType == long.class) return sqlType.equals("BIGINT");
        if (javaType == Integer.class || javaType == int.class) {
            return sqlType.equals("INT") || sqlType.equals("TINYINT");
        }
        if (javaType == Boolean.class || javaType == boolean.class) {
            return sqlType.equals("TINYINT") || sqlType.equals("BOOLEAN");
        }
        if (javaType == Double.class || javaType == double.class) return sqlType.equals("DOUBLE");
        if (javaType == BigDecimal.class) return sqlType.equals("DECIMAL");
        if (javaType == LocalDate.class) return sqlType.equals("DATE");
        if (javaType == LocalDateTime.class) return sqlType.equals("DATETIME");
        return true;
    }
}
