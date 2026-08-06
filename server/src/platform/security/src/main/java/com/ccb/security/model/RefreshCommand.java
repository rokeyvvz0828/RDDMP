package com.ccb.security.model;

import jakarta.validation.constraints.NotBlank;

public record RefreshCommand(@NotBlank(message = "refreshToken is required") String refreshToken) {
}
