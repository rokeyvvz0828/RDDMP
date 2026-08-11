package com.ccb.security.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordCommand(
        @NotBlank(message = "请输入原密码")
        String oldPassword,
        @NotBlank(message = "请输入新密码")
        @Size(min = 6, max = 64, message = "新密码长度应为6到64位")
        String newPassword,
        @NotBlank(message = "请确认新密码")
        @Size(min = 6, max = 64, message = "确认密码长度应为6到64位")
        String confirmPassword) {
}
