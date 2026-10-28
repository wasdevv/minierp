-- Database for the integration tests (MINIERP_TEST_JDBC_URL). They truncate every table before each test,
-- so it must never be the development database.
CREATE DATABASE mini_erp_test OWNER mini_erp;
