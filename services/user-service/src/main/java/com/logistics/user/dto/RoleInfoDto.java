package com.logistics.user.dto;

import com.logistics.common.security.Permission;
import com.logistics.common.security.Role;
import com.logistics.common.security.RolePermissions;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RoleInfoDto {

    private String role;
    private String displayName;
    private String description;
    private List<PermissionInfoDto> permissions;

    public RoleInfoDto() {
    }

    public RoleInfoDto(String role, String displayName, String description, List<PermissionInfoDto> permissions) {
        this.role = role;
        this.displayName = displayName;
        this.description = description;
        this.permissions = permissions;
    }

    public static RoleInfoDto fromRole(Role role) {
        String displayName;
        String description;
        switch (role) {
            case ADMIN -> {
                displayName = "Administrator (Quản trị viên)";
                description = "Toàn quyền quản trị hệ thống, quản lý người dùng, vai trò và giám sát toàn diện.";
            }
            case DISPATCHER -> {
                displayName = "Dispatcher (Nhân viên điều phối)";
                description = "Điều phối đơn hàng, phân công tài xế, quản lý hành trình và theo dõi chuyến hàng.";
            }
            case DRIVER -> {
                displayName = "Driver (Tài xế giao vận)";
                description = "Tiếp nhận đơn giao, cập nhật tiến độ vận chuyển và chia sẻ vị trí GPS thời gian thực.";
            }
            case CUSTOMER -> {
                displayName = "Customer (Khách hàng)";
                description = "Tạo đơn hàng vận chuyển, theo dõi tiến độ chuyến hàng và nhận thông báo trạng thái.";
            }
            default -> {
                displayName = role.name();
                description = "Vai trò người dùng trong hệ thống logistics.";
            }
        }

        Set<Permission> perms = RolePermissions.getPermissions(role);
        List<PermissionInfoDto> permissionDtos = perms.stream()
                .map(PermissionInfoDto::fromPermission)
                .collect(Collectors.toList());

        return new RoleInfoDto(role.name(), displayName, description, permissionDtos);
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<PermissionInfoDto> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<PermissionInfoDto> permissions) {
        this.permissions = permissions;
    }

    public static class PermissionInfoDto {
        private String name;
        private String code;
        private String description;

        public PermissionInfoDto() {
        }

        public PermissionInfoDto(String name, String code, String description) {
            this.name = name;
            this.code = code;
            this.description = description;
        }

        public static PermissionInfoDto fromPermission(Permission p) {
            return new PermissionInfoDto(p.name(), p.getCode(), p.getDescription());
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
