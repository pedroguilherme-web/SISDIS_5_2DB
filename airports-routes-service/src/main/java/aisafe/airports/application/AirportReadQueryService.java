package aisafe.airports.application;

import aisafe.airports.infrastructure.persistence.jpa.AirportReadView;
import aisafe.airports.infrastructure.persistence.jpa.AirportReadViewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class AirportReadQueryService {

    private final AirportReadViewRepository repository;

    public AirportReadQueryService(AirportReadViewRepository repository) {
        this.repository = repository;
    }

    public Page<AirportReadView> search(
            String name,
            String city,
            String country,
            Pageable pageable) {
        if (name != null && !name.isBlank()) {
                return repository.findByNameContainingIgnoreCaseAndDeletedFalse(name, pageable);
        }
        if (city != null && !city.isBlank()) {
                return repository.findByCityContainingIgnoreCaseAndDeletedFalse(city, pageable);
        }
        if (country != null && !country.isBlank()) {
                return repository.findByCountryContainingIgnoreCaseAndDeletedFalse(country, pageable);
        }
            return repository.findByDeletedFalse(pageable);
    }
}
