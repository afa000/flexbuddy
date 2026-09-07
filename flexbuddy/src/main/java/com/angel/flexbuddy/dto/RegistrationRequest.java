package com.angel.flexbuddy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrationRequest {

    @NotBlank(message = "Enter your name.")
    @Size(max = 80, message = "Your name must be 80 characters or fewer.")
    private String displayName;

    @NotBlank(message = "Enter your email address.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 254, message = "Your email address is too long.")
    private String email;

    @NotBlank(message = "Create a password.")
    @Size(min = 8, max = 72, message = "Your password must be between 8 and 72 characters.")
    private String password;
}
