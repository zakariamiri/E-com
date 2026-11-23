\set ON_ERROR_STOP on
\getenv auth_password AUTH_DB_PASSWORD
\getenv payment_password PAYMENT_DB_PASSWORD

CREATE ROLE auth_app LOGIN PASSWORD :'auth_password';
CREATE ROLE payment_app LOGIN PASSWORD :'payment_password';
CREATE DATABASE authdb OWNER auth_app;
CREATE DATABASE paymentdb OWNER payment_app;

\connect paymentdb
SET ROLE payment_app;
\i /opt/novastore/payment-v6.sql
