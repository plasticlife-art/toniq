package app.rubeton.toniq.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import lombok.Getter;

@Getter
public enum SyncLogScope implements EnumClass<String> {
    FULL_IMPORT("full_import"),
    EVENT_REFRESH("event_refresh"),
    AVAILABILITY_REFRESH("availability_refresh"),
    UNPUBLISH("unpublish"),
    WEBHOOK_AUDIT("webhook_audit");

    private final String id;

    SyncLogScope(final String id) {
        this.id = id;
    }

    public static SyncLogScope fromId(final String id) {
        for (SyncLogScope scope : values()) {
            if (scope.id.equals(id)) {
                return scope;
            }
        }
        return null;
    }
}
