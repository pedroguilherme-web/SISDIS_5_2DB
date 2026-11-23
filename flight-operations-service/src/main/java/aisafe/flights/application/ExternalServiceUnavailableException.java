package aisafe.flights.application;

public class ExternalServiceUnavailableException extends RuntimeException {
    private final String serviceName;

    public ExternalServiceUnavailableException(String serviceName) {
        super(serviceName + " is unavailable: no instance could be reached. Try again later.");
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}
