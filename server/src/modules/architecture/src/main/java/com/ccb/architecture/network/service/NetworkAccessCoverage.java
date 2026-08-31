package com.ccb.architecture.network.service;

import com.ccb.architecture.network.model.NetworkAccessModels.EndpointKind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** 网络访问端点快照覆盖判断；不能证明覆盖时返回 false。 */
final class NetworkAccessCoverage {
    private NetworkAccessCoverage() {
    }

    static boolean endpointCovers(ObjectMapper mapper, EndpointKind coveringKind, String coveringSnapshot,
                                  EndpointKind requestedKind, String requestedSnapshot) {
        if (coveringKind != requestedKind) {
            return false;
        }
        List<EndpointMember> covering = members(mapper, coveringKind, coveringSnapshot);
        List<EndpointMember> requested = members(mapper, requestedKind, requestedSnapshot);
        if (covering.isEmpty() || requested.isEmpty()) {
            return false;
        }
        for (EndpointMember needed : requested) {
            if (covering.stream().noneMatch(candidate -> memberCovers(candidate, needed))) {
                return false;
            }
        }
        return true;
    }

    static Set<Long> networkZoneIds(ObjectMapper mapper, EndpointKind kind, String snapshot) {
        Set<Long> zones = new LinkedHashSet<>();
        if (kind != EndpointKind.MANAGED) {
            return zones;
        }
        for (EndpointMember member : members(mapper, kind, snapshot)) {
            if (member.networkZoneId() != null && member.networkZoneId() > 0) {
                zones.add(member.networkZoneId());
            }
        }
        return zones;
    }

    static Set<Long> managedInstanceIds(ObjectMapper mapper, EndpointKind kind, String snapshot) {
        Set<Long> ids = new LinkedHashSet<>();
        if (kind != EndpointKind.MANAGED) {
            return ids;
        }
        for (EndpointMember member : members(mapper, kind, snapshot)) {
            if (member.instanceId() != null && member.instanceId() > 0) {
                ids.add(member.instanceId());
            }
        }
        return ids;
    }

    static List<ManagedEndpointAddress> managedEndpointAddresses(ObjectMapper mapper, EndpointKind kind,
                                                                 String snapshot) {
        if (kind != EndpointKind.MANAGED) {
            return List.of();
        }
        return members(mapper, kind, snapshot, false).stream()
                .map(member -> new ManagedEndpointAddress(member.instanceId(), member.networkZoneId(),
                        member.addressValue(), member.displayName()))
                .toList();
    }

    private static List<EndpointMember> members(ObjectMapper mapper, EndpointKind kind, String snapshot) {
        return members(mapper, kind, snapshot, true);
    }

    private static List<EndpointMember> members(ObjectMapper mapper, EndpointKind kind, String snapshot,
                                                boolean requireAddressEvidence) {
        if (snapshot == null || snapshot.isBlank()) {
            return List.of();
        }
        Objects.requireNonNull(mapper, "JSON 序列化器不能为空");
        try {
            JsonNode root = mapper.readTree(snapshot);
            if (!root.isArray()) {
                return List.of();
            }
            List<EndpointMember> members = new ArrayList<>();
            for (JsonNode item : root) {
                if (kind == EndpointKind.MANAGED) {
                    members.add(new EndpointMember(
                            longValue(item, "id"),
                            null,
                            longValue(item, "networkZoneId"),
                            "IP",
                            text(item, "ipAddress"),
                            text(item, "machineName")));
                } else {
                    members.add(new EndpointMember(
                            null,
                            longValue(item, "addressId"),
                            null,
                            text(item, "addressType"),
                            text(item, "addressValue"),
                            text(item, "displayName")));
                }
            }
            return requireAddressEvidence ? members.stream().filter(EndpointMember::hasAddressEvidence).toList()
                    : members;
        } catch (RuntimeException exception) {
            return List.of();
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static boolean memberCovers(EndpointMember covering, EndpointMember requested) {
        if (requested.instanceId() != null) {
            return Objects.equals(covering.instanceId(), requested.instanceId())
                    && addressCovers(covering.addressType(), covering.addressValue(),
                    requested.addressType(), requested.addressValue());
        }
        if (requested.addressId() != null && Objects.equals(covering.addressId(), requested.addressId())) {
            return addressCovers(covering.addressType(), covering.addressValue(),
                    requested.addressType(), requested.addressValue());
        }
        return addressCovers(covering.addressType(), covering.addressValue(),
                requested.addressType(), requested.addressValue());
    }

    private static boolean addressCovers(String coveringType, String coveringValue,
                                         String requestedType, String requestedValue) {
        if (coveringValue == null || coveringValue.isBlank()
                || requestedValue == null || requestedValue.isBlank()) {
            return false;
        }
        String coverType = normalizedType(coveringType, coveringValue);
        String requestType = normalizedType(requestedType, requestedValue);
        String cover = coveringValue.trim();
        String request = requestedValue.trim();
        if ("DOMAIN".equals(coverType) || "DOMAIN".equals(requestType)) {
            return cover.equalsIgnoreCase(request);
        }
        try {
            if ("CIDR".equals(coverType) && "CIDR".equals(requestType)) {
                NetworkCidr.ParsedSubnet covering = NetworkCidr.parseCidr(cover);
                NetworkCidr.ParsedSubnet requested = NetworkCidr.parseCidr(request);
                return covering.contains(requested.network()) && covering.contains(requested.broadcast());
            }
            if ("CIDR".equals(coverType)) {
                return NetworkCidr.contains(cover, request);
            }
            if ("CIDR".equals(requestType)) {
                return false;
            }
            return NetworkCidr.parseIpv4(cover) == NetworkCidr.parseIpv4(request);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String normalizedType(String type, String value) {
        if (type != null && !type.isBlank()) {
            return type.trim().toUpperCase(Locale.ROOT);
        }
        return value != null && value.contains("/") ? "CIDR" : "IP";
    }

    private static Long longValue(JsonNode item, String field) {
        JsonNode value = item.get(field);
        return value == null || value.isNull() || !value.canConvertToLong() ? null : value.asLong();
    }

    private static String text(JsonNode item, String field) {
        JsonNode value = item.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private record EndpointMember(Long instanceId, Long addressId, Long networkZoneId,
                                  String addressType, String addressValue, String displayName) {
        boolean hasAddressEvidence() {
            return addressValue != null && !addressValue.isBlank();
        }
    }

    record ManagedEndpointAddress(Long instanceId, Long networkZoneId, String ipAddress, String displayName) {
    }
}
