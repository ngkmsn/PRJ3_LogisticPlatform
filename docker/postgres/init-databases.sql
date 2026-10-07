-- =============================================================
-- Logistics Platform - Multi-Database Initialization Script
-- =============================================================
-- This script runs once on first startup of the PostgreSQL container
-- to provision separate databases for each domain microservice,
-- following the Database-per-Service architectural pattern.
-- =============================================================

CREATE DATABASE user_db;
CREATE DATABASE order_db;
CREATE DATABASE shipment_db;
CREATE DATABASE notification_db;

-- Grant privileges to the default user
GRANT ALL PRIVILEGES ON DATABASE user_db TO postgres;
GRANT ALL PRIVILEGES ON DATABASE order_db TO postgres;
GRANT ALL PRIVILEGES ON DATABASE shipment_db TO postgres;
GRANT ALL PRIVILEGES ON DATABASE notification_db TO postgres;
