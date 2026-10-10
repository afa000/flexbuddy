package com.angel.flexbuddy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrationRequest {

    @NotBlank(message = "{validation.name.required}")
    @Size(max = 80, message = "{validation.name.tooLong}")
    private String displayName;

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    @Size(max = 254, message = "{validation.email.tooLong}")
    private String email;

    @NotBlank(message = "{validation.password.create}")
    @Size(min = 8, max = 72, message = "{validation.password.length}")
    private String password;
}
