package com.ziyadsamhaoui.messagingnotificationservice.security;

import com.ziyadsamhaoui.messagingnotificationservice.exception.UnauthenticatedRequestException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

@Component
public class CurrentUserProvider {

    public UUID requireUserId() {
        Jwt jwt = currentJwt()
                .orElseThrow(() -> new UnauthenticatedRequestException("A valid bearer token is required"));

        String subject = jwt.getSubject();
        if (!StringUtils.hasText(subject)) {
            throw new UnauthenticatedRequestException("The access token does not identify a user");
        }

        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw new UnauthenticatedRequestException("The access token subject is not a valid user id");
        }
    }

    private Optional<Jwt> currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication
                && jwtAuthentication.isAuthenticated()) {
            return Optional.of(jwtAuthentication.getToken());
        }
        return Optional.empty();
    }
}
