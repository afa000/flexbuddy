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
    @Size(max = 72, message = "{validation.password.tooLong}")
    private String password;

    /** The emailed code that stands in for the password on an account created with Google. */
    @Size(max = 7, message = "{validation.code.sixDigits}")
    private String code;

    @NotBlank(message = "{validation.delete.required}")
    @Pattern(regexp = "delete", message = "{validation.delete.exact}")
    private String confirmation;
}
