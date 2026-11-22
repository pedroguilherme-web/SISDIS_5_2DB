package aisafe.flights.application;

public class RemoteRequestRejectedException extends RuntimeException {
    private final String serviceName;
    private final int status;

    public RemoteRequestRejectedException(String serviceName, int status, String detail) {
        super(serviceName + " rejected the request (HTTP " + status + ")"
                + (detail == null || detail.isBlank() ? "" : ": " + detail));
        this.serviceName = serviceName;
        this.status = status;
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getStatus() {
        return status;
    }
}
