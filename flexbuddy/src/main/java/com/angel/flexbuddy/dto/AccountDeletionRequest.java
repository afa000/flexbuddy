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

    /** Required, and checked by the controller, for an account that has a password. */
    @Size(max = 72, message = "Password must be 72 characters or fewer.")
    private String password;

    /** The emailed code that stands in for the password on an account created with Google. */
    @Size(max = 7, message = "Enter the 6-digit code.")
    private String code;

    @NotBlank(message = "Type delete to confirm.")
    @Pattern(regexp = "delete", message = "Type delete exactly as shown.")
    private String confirmation;
}
