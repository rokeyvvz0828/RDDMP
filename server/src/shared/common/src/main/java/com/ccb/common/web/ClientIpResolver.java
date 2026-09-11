package com.ccb.common.web;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Resolves a client IP without trusting forwarding headers from public direct connections. */
public final class ClientIpResolver {
    private ClientIpResolver() {
    }

    public static String resolve(String xForwardedFor, String remoteAddress) {
        ParsedAddress remote = parse(remoteAddress);
        List<ParsedAddress> forwarded = forwardedAddresses(xForwardedFor);
        if (remote == null) {
            return forwarded.isEmpty() ? null : forwarded.get(0).literal();
        }
        if (!isTrustedProxy(remote.address()) || forwarded.isEmpty()) {
            return remote.literal();
        }

        List<ParsedAddress> chain = new ArrayList<>(forwarded);
        chain.add(remote);
        for (int index = chain.size() - 1; index >= 0; index--) {
            ParsedAddress candidate = chain.get(index);
            if (!isTrustedProxy(candidate.address())) {
                return candidate.literal();
            }
        }
        return chain.get(0).literal();
    }

    private static List<ParsedAddress> forwardedAddresses(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<ParsedAddress> addresses = new ArrayList<>();
        for (String part : value.split(",")) {
            ParsedAddress parsed = parse(part);
            if (parsed != null) {
                addresses.add(parsed);
            }
        }
        return addresses;
    }

    private static ParsedAddress parse(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.isBlank() || "unknown".equals(value.toLowerCase(Locale.ROOT))) {
            return null;
        }
        if (value.startsWith("[") && value.contains("]")) {
            value = value.substring(1, value.indexOf(']'));
        } else if (value.indexOf(':') >= 0 && value.indexOf(':') == value.lastIndexOf(':') && value.contains(".")) {
            value = value.substring(0, value.indexOf(':'));
        }
        int scopeIndex = value.indexOf('%');
        if (scopeIndex >= 0) {
            value = value.substring(0, scopeIndex);
        }

        try {
            if (value.contains(":")) {
                InetAddress address = InetAddress.getByName(value);
                return address instanceof Inet6Address ? new ParsedAddress(address.getHostAddress(), address) : null;
            }
            String ipv4 = normalizedIpv4(value);
            if (ipv4 == null) {
                return null;
            }
            InetAddress address = InetAddress.getByName(ipv4);
            return address instanceof Inet4Address ? new ParsedAddress(ipv4, address) : null;
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private static String normalizedIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        int[] octets = new int[4];
        for (int index = 0; index < parts.length; index++) {
            if (parts[index].isEmpty() || !parts[index].chars().allMatch(Character::isDigit)) {
                return null;
            }
            try {
                octets[index] = Integer.parseInt(parts[index]);
            } catch (NumberFormatException exception) {
                return null;
            }
            if (octets[index] > 255) {
                return null;
            }
        }
        return octets[0] + "." + octets[1] + "." + octets[2] + "." + octets[3];
    }

    private static boolean isTrustedProxy(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first == 100 && second >= 64 && second <= 127;
        }
        int first = Byte.toUnsignedInt(bytes[0]);
        return address instanceof Inet6Address && (first & 0xfe) == 0xfc;
    }

    private record ParsedAddress(String literal, InetAddress address) {
    }
}
