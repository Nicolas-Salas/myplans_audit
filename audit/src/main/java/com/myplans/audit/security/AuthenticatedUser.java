package com.myplans.audit.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@ToString
public class AuthenticatedUser {
    private final Integer idUsuario;
    private final String email;
    private final List<String> roles;
}