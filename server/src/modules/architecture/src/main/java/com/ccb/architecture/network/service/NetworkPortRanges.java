package com.ccb.architecture.network.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 网络访问端口列表与闭区间覆盖判断。 */
final class NetworkPortRanges {
    private final List<Range> ranges;

    private NetworkPortRanges(List<Range> ranges) {
        this.ranges = List.copyOf(ranges);
    }

    static NetworkPortRanges parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("端口不能为空");
        }
        List<Range> parsed = new ArrayList<>();
        for (String token : value.split(",")) {
            String part = token.trim();
            if (part.isEmpty()) {
                throw new IllegalArgumentException("端口不能为空");
            }
            int dash = part.indexOf('-');
            if (dash < 0) {
                int port = parsePort(part);
                parsed.add(new Range(port, port));
            } else if (dash == part.lastIndexOf('-')) {
                int start = parsePort(part.substring(0, dash).trim());
                int end = parsePort(part.substring(dash + 1).trim());
                if (end < start) {
                    throw new IllegalArgumentException("端口范围结束值不能小于开始值");
                }
                parsed.add(new Range(start, end));
            } else {
                throw new IllegalArgumentException("端口范围格式无效");
            }
        }
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("端口不能为空");
        }
        parsed.sort((left, right) -> Integer.compare(left.start(), right.start()));
        List<Range> normalized = new ArrayList<>();
        for (Range range : parsed) {
            if (normalized.isEmpty()) {
                normalized.add(range);
                continue;
            }
            Range last = normalized.get(normalized.size() - 1);
            if (range.start() <= last.end() + 1) {
                normalized.set(normalized.size() - 1, new Range(last.start(), Math.max(last.end(), range.end())));
            } else {
                normalized.add(range);
            }
        }
        return new NetworkPortRanges(normalized);
    }

    boolean containsAll(NetworkPortRanges requested) {
        Objects.requireNonNull(requested, "请求端口不能为空");
        int coveringIndex = 0;
        for (Range needed : requested.ranges) {
            int cursor = needed.start();
            while (cursor <= needed.end()) {
                while (coveringIndex < ranges.size() && ranges.get(coveringIndex).end() < cursor) {
                    coveringIndex++;
                }
                if (coveringIndex >= ranges.size() || ranges.get(coveringIndex).start() > cursor) {
                    return false;
                }
                cursor = ranges.get(coveringIndex).end() + 1;
            }
        }
        return true;
    }

    private static int parsePort(String value) {
        if (value == null || value.isBlank() || !value.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("端口必须为 1-65535 的整数");
        }
        int port;
        try {
            port = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("端口必须为 1-65535 的整数");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("端口必须为 1-65535 的整数");
        }
        return port;
    }

    private record Range(int start, int end) {
    }
}
