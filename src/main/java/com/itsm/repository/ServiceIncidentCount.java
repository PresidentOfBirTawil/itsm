package com.itsm.repository;

/** One row of the "incidents per service" report. */
public record ServiceIncidentCount(String serviceName, int criticality, long incidents) {
}
