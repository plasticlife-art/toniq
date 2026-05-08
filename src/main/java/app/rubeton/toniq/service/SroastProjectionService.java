package app.rubeton.toniq.service;

import app.rubeton.toniq.entity.Event;

public interface SroastProjectionService {

    boolean existsForEvent(Event event);
}
