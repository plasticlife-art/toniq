package app.rubeton.toniq.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import lombok.Getter;

@Getter
public enum SyncLogStatus implements EnumClass<String> {
    STARTED("started"),
    SUCCESS("success"),
    FAILURE("failure");

    private final String id;

    SyncLogStatus(final String id) {
        this.id = id;
    }

    public static SyncLogStatus fromId(final String id) {
        for (SyncLogStatus status : values()) {
            if (status.id.equals(id)) {
                return status;
            }
        }
        return null;
    }
}
