package com.angel.flexbuddy.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

    private String token;

    @NotBlank(message = "{validation.password.create}")
    @Size(min = 8, max = 72, message = "{validation.password.length}")
    private String password;

    private String confirmPassword;

    @AssertTrue(message = "{validation.password.mismatch}")
    public boolean isMatching() {
        return password != null && password.equals(confirmPassword);
    }
}
