package com.itsm.error;

/** Thrown when an incident is reported against an IT service that does not exist. */
public class ServiceNotFoundException extends NotFoundException {

    public ServiceNotFoundException(Long serviceId) {
        super("There is no IT service with the number " + serviceId + ".");
    }
}
