package com.logistics.user.dto;

import com.logistics.user.entity.UserRole;
import jakarta.validation.constraints.NotNull;

public class UpdateUserRoleRequest {

    @NotNull(message = "Role is required")
    private UserRole role;

    public UpdateUserRoleRequest() {
    }

    public UpdateUserRoleRequest(UserRole role) {
        this.role = role;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }
}
