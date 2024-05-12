package com.bts.personalbudget.core.domain.repository;

import org.testcontainers.containers.MySQLContainer;

public class MySQLContainerConfig extends MySQLContainer<MySQLContainerConfig> {

    private static MySQLContainerConfig container;
    private static final String IMAGE_VERSION = "mysql:latest";

    private MySQLContainerConfig() {
        super(IMAGE_VERSION);
    }

    public static MySQLContainerConfig getInstance() {
        if (container == null) {
            container = new MySQLContainerConfig();
        }
        return container;
    }

    @Override
    public void start() {
        super.start();
        System.setProperty("DB_URL", container.getJdbcUrl());
        System.setProperty("DB_USERNAME", container.getUsername());
        System.setProperty("DB_PASSWORD", container.getPassword());
        System.setProperty("DB_DRIVER_CLASS_NAME", container.getDriverClassName());
    }

    @Override
    public void stop() {
        //do nothing, JVM handles shut down
    }
}
