package com.ccb.security.model;

import jakarta.validation.constraints.NotBlank;

public record LoginCommand(@NotBlank(message = "username is required") String username,
                           @NotBlank(message = "password is required") String password) {
}
