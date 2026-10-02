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

    @NotBlank(message = "Create a password.")
    @Size(min = 8, max = 72, message = "Your password must be between 8 and 72 characters.")
    private String password;

    private String confirmPassword;

    @AssertTrue(message = "The two passwords do not match.")
    public boolean isMatching() {
        return password != null && password.equals(confirmPassword);
    }
}
