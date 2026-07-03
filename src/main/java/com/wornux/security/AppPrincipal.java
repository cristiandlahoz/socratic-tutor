package com.wornux.security;

import java.util.UUID;

public record AppPrincipal(UUID accountId, String email, boolean locked) {
}
