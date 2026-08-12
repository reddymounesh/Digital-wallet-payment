package com.stark.wallet.wallet_app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequestDto {
    @NotBlank @Email
    private String email;
    @NotBlank
    @Size(min = 8)
    private String password;

}
