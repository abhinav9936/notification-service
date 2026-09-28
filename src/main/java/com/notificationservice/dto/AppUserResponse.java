package com.notificationservice.dto;

import com.notificationservice.entity.AppUser;
import com.notificationservice.entity.AppUserRole;

public record AppUserResponse(
        Long id,
        String username,
        AppUserRole role,
        Long tenantId
) {
    public static AppUserResponse from(AppUser appUser) {
        return new AppUserResponse(
                appUser.getId(),
                appUser.getUsername(),
                appUser.getRole(),
                appUser.getTenant() != null ? appUser.getTenant().getId() : null
        );
    }
}
