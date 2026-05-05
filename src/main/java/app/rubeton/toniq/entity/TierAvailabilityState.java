package app.rubeton.toniq.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import lombok.Getter;

@Getter
public enum TierAvailabilityState implements EnumClass<String> {
    AVAILABLE("available"),
    LOW("low"),
    SOLD_OUT("sold_out"),
    UNKNOWN("unknown");

    private final String id;

    TierAvailabilityState(final String id) {
        this.id = id;
    }

    public static TierAvailabilityState fromId(final String id) {
        for (TierAvailabilityState state : values()) {
            if (state.id.equals(id)) {
                return state;
            }
        }
        return null;
    }
}
