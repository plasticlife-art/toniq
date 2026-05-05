package app.rubeton.toniq.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import lombok.Getter;

@Getter
public enum PublicationState implements EnumClass<String> {
    DRAFT("draft"),
    PUBLISHED("published"),
    UNPUBLISHED("unpublished");

    private final String id;

    PublicationState(final String id) {
        this.id = id;
    }

    public static PublicationState fromId(final String id) {
        for (PublicationState state : values()) {
            if (state.id.equals(id)) {
                return state;
            }
        }
        return null;
    }
}
