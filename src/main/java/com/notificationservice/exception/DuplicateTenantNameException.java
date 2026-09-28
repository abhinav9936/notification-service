package com.notificationservice.exception;

public class DuplicateTenantNameException extends RuntimeException{
    public DuplicateTenantNameException(String name){
        super("Tenant name already in use: " + name);
    }
}
