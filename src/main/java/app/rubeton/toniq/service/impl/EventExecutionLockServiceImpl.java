package app.rubeton.toniq.service.impl;

import app.rubeton.toniq.service.EventExecutionLockService;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class EventExecutionLockServiceImpl implements EventExecutionLockService {

    private final ConcurrentHashMap<String, EventLockEntry> eventLocks = new ConcurrentHashMap<>();

    @Override
    public EventExecutionLockHandle tryAcquire(final String eventId) {
        EventLockEntry lockEntry = eventLocks.compute(eventId, (ignored, existing) -> {
            EventLockEntry entry = existing != null ? existing : new EventLockEntry();
            entry.retain();
            return entry;
        });
        boolean acquired = lockEntry.lock().tryLock();
        return new EventExecutionLockHandleImpl(eventId, lockEntry, acquired);
    }

    @Override
    public int activeLockCount() {
        return eventLocks.size();
    }

    private void releaseEventLockEntry(final String eventId, final EventLockEntry expectedEntry) {
        eventLocks.computeIfPresent(eventId, (ignored, existing) -> {
            if (existing != expectedEntry) {
                return existing;
            }
            int references = existing.release();
            if (references < 0) {
                throw new IllegalStateException("Event lock ref-count became negative for event " + eventId);
            }
            return references == 0 ? null : existing;
        });
    }

    private static final class EventLockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicInteger references = new AtomicInteger();

        private ReentrantLock lock() {
            return lock;
        }

        private void retain() {
            references.incrementAndGet();
        }

        private int release() {
            return references.decrementAndGet();
        }
    }

    private final class EventExecutionLockHandleImpl implements EventExecutionLockHandle {
        private final String eventId;
        private final EventLockEntry lockEntry;
        private final boolean acquired;
        private boolean closed;

        private EventExecutionLockHandleImpl(final String eventId, final EventLockEntry lockEntry, final boolean acquired) {
            this.eventId = eventId;
            this.lockEntry = lockEntry;
            this.acquired = acquired;
        }

        @Override
        public boolean acquired() {
            return acquired;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (acquired) {
                lockEntry.lock().unlock();
            }
            releaseEventLockEntry(eventId, lockEntry);
        }
    }
}
