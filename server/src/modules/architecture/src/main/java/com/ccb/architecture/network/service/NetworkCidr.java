package com.ccb.architecture.network.service;

import java.util.Objects;
import java.util.Set;

/** IPv4 CIDR 校验与 Mock 地址建议工具。 */
public final class NetworkCidr {
    private static final long IPV4_MASK = 0xFFFF_FFFFL;

    private NetworkCidr() {
    }

    public record ParsedSubnet(String cidrBlock, long network, long broadcast, int prefixLength) {
        boolean contains(long address) {
            return address >= network && address <= broadcast;
        }

        long firstUsable() {
            return prefixLength >= 31 ? network : network + 1;
        }

        long lastUsable() {
            return prefixLength >= 31 ? broadcast : broadcast - 1;
        }
    }

    public static String normalizeCidr(String value) {
        return parseCidr(value).cidrBlock();
    }

    public static boolean contains(String cidrBlock, String ipAddress) {
        return parseCidr(cidrBlock).contains(parseIpv4(ipAddress));
    }

    public static boolean contains(ParsedSubnet subnet, String ipAddress) {
        return subnet.contains(parseIpv4(ipAddress));
    }

    public static String suggestAddress(String cidrBlock, int ordinal, Set<String> excludedIps) {
        ParsedSubnet subnet = parseCidr(cidrBlock);
        long start = subnet.firstUsable();
        long end = subnet.lastUsable();
        if (start > end) {
            throw new IllegalArgumentException("CIDR 网段没有可用 IPv4 地址: " + cidrBlock);
        }
        long size = end - start + 1;
        long offset = Math.floorMod(Math.max(ordinal, 1) - 1L, size);
        long attempts = Math.min(size, 4096L);
        for (long i = 0; i < attempts; i++) {
            String candidate = toIpv4(start + ((offset + i) % size));
            if (excludedIps == null || excludedIps.add(candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("CIDR 网段可用 IPv4 地址已被当前批次占用: " + cidrBlock);
    }

    public static ParsedSubnet parseCidr(String value) {
        String normalized = Objects.requireNonNull(value, "CIDR 不能为空").trim();
        int slash = normalized.indexOf('/');
        if (slash <= 0 || slash != normalized.lastIndexOf('/')) {
            throw new IllegalArgumentException("CIDR 地址必须形如 10.16.32.0/20");
        }
        long address = parseIpv4(normalized.substring(0, slash));
        int prefixLength;
        try {
            prefixLength = Integer.parseInt(normalized.substring(slash + 1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("CIDR 掩码必须为 0-32 的整数");
        }
        if (prefixLength < 0 || prefixLength > 32) {
            throw new IllegalArgumentException("CIDR 掩码必须为 0-32 的整数");
        }
        long mask = prefixLength == 0 ? 0 : (IPV4_MASK << (32 - prefixLength)) & IPV4_MASK;
        long network = address & mask;
        long broadcast = network | (~mask & IPV4_MASK);
        return new ParsedSubnet(toIpv4(network) + "/" + prefixLength, network, broadcast, prefixLength);
    }

    public static long parseIpv4(String value) {
        String normalized = Objects.requireNonNull(value, "IPv4 地址不能为空").trim();
        String[] parts = normalized.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("IPv4 地址格式无效: " + value);
        }
        long result = 0;
        for (String part : parts) {
            int segment;
            try {
                segment = Integer.parseInt(part);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("IPv4 地址格式无效: " + value);
            }
            if (segment < 0 || segment > 255) {
                throw new IllegalArgumentException("IPv4 地址段必须在 0-255 之间: " + value);
            }
            result = (result << 8) | segment;
        }
        return result & IPV4_MASK;
    }

    public static String toIpv4(long value) {
        long normalized = value & IPV4_MASK;
        return ((normalized >>> 24) & 255) + "."
                + ((normalized >>> 16) & 255) + "."
                + ((normalized >>> 8) & 255) + "."
                + (normalized & 255);
    }
}
