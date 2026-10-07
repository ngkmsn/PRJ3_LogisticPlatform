-- =============================================================
-- Logistics Platform - Database-per-Service Initialization Script
-- =============================================================
-- Strict isolation:
-- 1. Dedicated user & password per service
-- 2. Dedicated database owned by that service's user
-- 3. Cross-service database access is strictly revoked
-- =============================================================

-- 1. Create dedicated service users
CREATE USER user_service_user WITH PASSWORD 'user_password';
CREATE USER order_service_user WITH PASSWORD 'order_password';
CREATE USER shipment_service_user WITH PASSWORD 'shipment_password';
CREATE USER notification_service_user WITH PASSWORD 'notification_password';

-- 2. Create isolated databases owned by each service user
CREATE DATABASE user_db WITH OWNER user_service_user;
CREATE DATABASE order_db WITH OWNER order_service_user;
CREATE DATABASE shipment_db WITH OWNER shipment_service_user;
CREATE DATABASE notification_db WITH OWNER notification_service_user;

-- 3. Revoke public connect access to enforce isolation
REVOKE CONNECT ON DATABASE user_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE order_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE shipment_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE notification_db FROM PUBLIC;

-- 4. Grant connect privilege ONLY to the designated service user
GRANT CONNECT ON DATABASE user_db TO user_service_user;
GRANT CONNECT ON DATABASE order_db TO order_service_user;
GRANT CONNECT ON DATABASE shipment_db TO shipment_service_user;
GRANT CONNECT ON DATABASE notification_db TO notification_service_user;
