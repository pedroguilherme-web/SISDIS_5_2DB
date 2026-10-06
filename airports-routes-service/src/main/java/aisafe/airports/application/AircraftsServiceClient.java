package aisafe.airports.application;

import aisafe.airports.domain.ModelName;

public interface AircraftsServiceClient {
    boolean existsByModelName(ModelName modelName);
}
