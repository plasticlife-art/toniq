package app.rubeton.toniq.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import lombok.Getter;

@Getter
public enum SyncResult implements EnumClass<String> {
    SUCCESS("success"),
    FAILURE("failure");

    private final String id;

    SyncResult(final String id) {
        this.id = id;
    }

    public static SyncResult fromId(final String id) {
        for (SyncResult result : values()) {
            if (result.id.equals(id)) {
                return result;
            }
        }
        return null;
    }
}
