package app.rubeton.toniq.service;

public interface EventExecutionLockService {

    EventExecutionLockHandle tryAcquire(String eventId);

    int activeLockCount();

    interface EventExecutionLockHandle extends AutoCloseable {

        boolean acquired();

        @Override
        void close();
    }
}
