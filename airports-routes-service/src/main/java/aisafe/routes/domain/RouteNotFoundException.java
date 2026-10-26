package aisafe.routes.domain;

import aisafe.shared.domain.ResourceNotFoundException;

/**
 * Raised when the requested route cannot be found.
 */
public class RouteNotFoundException extends ResourceNotFoundException {
    public RouteNotFoundException(String id) {
        super("Route with ID " + id + " was not found.");
    }
}