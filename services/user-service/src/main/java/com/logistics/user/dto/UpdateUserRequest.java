package com.logistics.user.dto;

import com.logistics.user.entity.UserRole;
import com.logistics.user.entity.UserStatus;
import jakarta.validation.constraints.Email;

public class UpdateUserRequest {

    @Email(message = "Email format is invalid")
    private String email;

    private UserRole role;

    private UserStatus status;

    public UpdateUserRequest() {
    }

    public UpdateUserRequest(String email, UserRole role, UserStatus status) {
        this.email = email;
        this.role = role;
        this.status = status;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "UpdateUserRequest{" +
                "email='" + email + '\'' +
                ", role=" + role +
                ", status=" + status +
                '}';
    }
}
