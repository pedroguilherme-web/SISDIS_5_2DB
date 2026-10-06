package aisafe.airports.domain;

import aisafe.shared.domain.DomainException;
import java.util.Objects;

public class ModelName {
    private final String name;

    public ModelName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new DomainException("Model name cannot be empty.");
        }
        this.name = name.trim();
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModelName modelName = (ModelName) o;
        return Objects.equals(name, modelName.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
