package aisafe.shared.domain;

/**
 * Base domain exception for resources that are not found.
 */
public class ResourceNotFoundException extends DomainException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
