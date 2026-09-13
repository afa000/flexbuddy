package com.angel.flexbuddy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AccountDeletionRequest {

    @NotBlank(message = "Enter your password.")
    @Size(max = 72, message = "Password must be 72 characters or fewer.")
    private String password;

    @NotBlank(message = "Type delete to confirm.")
    @Pattern(regexp = "delete", message = "Type delete exactly as shown.")
    private String confirmation;
}
