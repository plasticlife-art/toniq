package app.rubeton.toniq.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

public enum PublicationMode implements EnumClass<String> {
    AUTO("auto"),
    ON("on"),
    OFF("off");

    private final String id;

    PublicationMode(final String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    public static PublicationMode fromId(final String id) {
        for (PublicationMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return null;
    }
}
