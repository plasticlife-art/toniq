package app.rubeton.toniq.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import lombok.Getter;

@Getter
public enum OverrideStatus implements EnumClass<String> {
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    RESCHEDULED("rescheduled");

    private final String id;

    OverrideStatus(final String id) {
        this.id = id;
    }

    public static OverrideStatus fromId(final String id) {
        for (OverrideStatus status : values()) {
            if (status.id.equals(id)) {
                return status;
            }
        }
        return null;
    }
}
