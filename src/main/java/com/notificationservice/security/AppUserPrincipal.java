package com.notificationservice.security;

import com.notificationservice.entity.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class AppUserPrincipal implements UserDetails {

    private final AppUser appUser;

    public AppUserPrincipal(AppUser appUser){
        this.appUser = appUser;
    }

    public Long getUserId(){
        return appUser.getId();
    }

    public Long getTenantId(){
        return appUser.getTenant() != null ? appUser.getTenant().getId() : null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities(){
        return List.of(new SimpleGrantedAuthority("ROLE_" + appUser.getRole().name()));
    }

    @Override
    public String getPassword(){
        return appUser.getPasswordHash();
    }

    @Override
    public String getUsername(){
        return appUser.getUsername();
    }

}
