package com.ccb.requirement.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** 测试用 JdbcTemplate：记录 update 语句、按 SQL 内容返回 count 或列表结果。 */
public final class StubJdbcTemplate extends JdbcTemplate {
    private final List<String> updates = new ArrayList<>();
    private final Function<String, Long> countResolver;
    private final List<Map<String, Object>> listResult;
    private final Map<String, Object> mapResult;

    public StubJdbcTemplate() {
        this(sql -> 0L, List.of(), Map.of());
    }

    public StubJdbcTemplate(Function<String, Long> countResolver,
                            List<Map<String, Object>> listResult,
                            Map<String, Object> mapResult) {
        this.countResolver = countResolver;
        this.listResult = listResult;
        this.mapResult = mapResult;
    }

    public List<String> updates() {
        return updates;
    }

    @Override
    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return listResult;
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
        Long count = countResolver.apply(sql);
        if (Integer.class.equals(requiredType)) {
            return requiredType.cast(count.intValue());
        }
        return requiredType.cast(count);
    }

    @Override
    public Map<String, Object> queryForMap(String sql, Object... args) {
        return mapResult;
    }

    @Override
    public int update(String sql, Object... args) {
        updates.add(sql);
        return 1;
    }
}
