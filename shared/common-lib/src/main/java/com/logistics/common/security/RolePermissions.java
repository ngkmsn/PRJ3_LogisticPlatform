package com.logistics.common.security;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class RolePermissions {

    private static final Map<Role, Set<Permission>> ROLE_PERMISSIONS_MAP;

    static {
        Map<Role, Set<Permission>> map = new EnumMap<>(Role.class);

        // ADMINISTRATOR has all permissions
        map.put(Role.ADMIN, Collections.unmodifiableSet(EnumSet.allOf(Permission.class)));

        // DISPATCHER manages orders, assigns shipments, views users and sends notifications
        map.put(Role.DISPATCHER, Collections.unmodifiableSet(EnumSet.of(
                Permission.USER_READ,
                Permission.DRIVER_READ,
                Permission.DRIVER_CREATE,
                Permission.DRIVER_UPDATE,
                Permission.ORDER_READ,
                Permission.ORDER_UPDATE,
                Permission.SHIPMENT_READ,
                Permission.SHIPMENT_ASSIGN,
                Permission.SHIPMENT_UPDATE_STATUS,
                Permission.NOTIFICATION_READ,
                Permission.NOTIFICATION_SEND
        )));

        // DRIVER views assigned shipments, updates delivery status, pings GPS, reads notifications, manages self profile
        map.put(Role.DRIVER, Collections.unmodifiableSet(EnumSet.of(
                Permission.DRIVER_READ,
                Permission.DRIVER_UPDATE,
                Permission.SHIPMENT_READ,
                Permission.SHIPMENT_UPDATE_STATUS,
                Permission.SHIPMENT_LOCATION_PING,
                Permission.NOTIFICATION_READ
        )));

        // CUSTOMER creates, reads, and cancels their orders, reads notifications
        map.put(Role.CUSTOMER, Collections.unmodifiableSet(EnumSet.of(
                Permission.ORDER_CREATE,
                Permission.ORDER_READ,
                Permission.ORDER_CANCEL,
                Permission.NOTIFICATION_READ
        )));

        ROLE_PERMISSIONS_MAP = Collections.unmodifiableMap(map);
    }

    private RolePermissions() {
    }

    public static Set<Permission> getPermissions(Role role) {
        if (role == null) {
            return Collections.emptySet();
        }
        return ROLE_PERMISSIONS_MAP.getOrDefault(role, Collections.emptySet());
    }

    public static boolean hasPermission(Role role, Permission permission) {
        if (role == null || permission == null) {
            return false;
        }
        return getPermissions(role).contains(permission);
    }

    public static Map<Role, Set<Permission>> getAllRolePermissions() {
        return ROLE_PERMISSIONS_MAP;
    }
}
