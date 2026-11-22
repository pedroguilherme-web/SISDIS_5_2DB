package aisafe.unicorns.application;

import aisafe.unicorns.infrastructure.persistence.AirplaneMoodViewRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AirplaneMoodQueryService {

    private final AirplaneMoodViewRepository repository;

    public AirplaneMoodQueryService(AirplaneMoodViewRepository repository) {
        this.repository = repository;
    }

    public List<AirplaneMoodResponse> findAll() {
        return repository.findAllByOrderByRegistrationAsc().stream()
                .map(view -> new AirplaneMoodResponse(
                        view.getRegistration(),
                        view.getMood(),
                        view.getUpdatedAt()))
                .toList();
    }
}
