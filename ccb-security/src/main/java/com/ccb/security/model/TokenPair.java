package com.ccb.security.model;

public record TokenPair(String accessToken, String refreshToken, long accessExpiresInSeconds,
                        long refreshExpiresInSeconds, String tokenType) {
}
