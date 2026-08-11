package com.ex.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @JsonAlias({"username", "email"})
        @NotBlank @Size(max = 120) String identifier,
        @NotBlank @Size(max = 100) String password
) {
    public String username() {
        return identifier;
    }

    public String email() {
        return identifier;
    }
}
