package com.logistics.common.security;

public enum Permission {
    // User & Identity Management
    USER_READ("user:read", "Xem danh sách và thông tin người dùng"),
    USER_CREATE("user:create", "Tạo tài khoản người dùng mới"),
    USER_UPDATE("user:update", "Cập nhật thông tin người dùng"),
    USER_STATUS_CHANGE("user:status_change", "Khóa hoặc mở khóa tài khoản người dùng"),
    
    // Role & RBAC Management
    ROLE_READ("role:read", "Xem danh sách vai trò và quyền hạn hỗ trợ"),
    ROLE_UPDATE("role:update", "Thay đổi vai trò của người dùng"),

    // Driver Profile Operations
    DRIVER_READ("driver:read", "Xem danh sách và thông tin hồ sơ tài xế"),
    DRIVER_CREATE("driver:create", "Tạo hồ sơ tài xế mới"),
    DRIVER_UPDATE("driver:update", "Cập nhật thông tin hồ sơ tài xế"),

    // Order Operations
    ORDER_CREATE("order:create", "Tạo đơn hàng vận chuyển mới"),
    ORDER_READ("order:read", "Xem thông tin đơn hàng"),
    ORDER_UPDATE("order:update", "Cập nhật thông tin đơn hàng"),
    ORDER_CANCEL("order:cancel", "Hủy đơn hàng"),

    // Shipment & Delivery Operations
    SHIPMENT_READ("shipment:read", "Xem danh sách và chi tiết vận đơn/chuyến hàng"),
    SHIPMENT_ASSIGN("shipment:assign", "Điều phối và phân công tài xế/phương tiện"),
    SHIPMENT_UPDATE_STATUS("shipment:update_status", "Cập nhật trạng thái chuyến hàng"),
    SHIPMENT_LOCATION_PING("shipment:location_ping", "Gửi vị trí định vị GPS của chuyến hàng"),

    // Notification Operations
    NOTIFICATION_READ("notification:read", "Xem danh sách thông báo"),
    NOTIFICATION_SEND("notification:send", "Gửi thông báo hệ thống");

    private final String code;
    private final String description;

    Permission(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static Permission fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (Permission permission : values()) {
            if (permission.code.equalsIgnoreCase(code.trim())) {
                return permission;
            }
        }
        return null;
    }
}
