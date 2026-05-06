package app.rubeton.toniq.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import lombok.Getter;

@Getter
public enum SyncLogTriggerSource implements EnumClass<String> {
    WEBHOOK_ENABLE("webhook_enable"),
    WEBHOOK_DISABLE("webhook_disable"),
    WEBHOOK_UNSUPPORTED("webhook_unsupported"),
    POLLING("polling"),
    MANUAL("manual");

    private final String id;

    SyncLogTriggerSource(final String id) {
        this.id = id;
    }

    public static SyncLogTriggerSource fromId(final String id) {
        for (SyncLogTriggerSource source : values()) {
            if (source.id.equals(id)) {
                return source;
            }
        }
        return null;
    }
}
