package com.yucheng.drama.kernel.api;

import java.util.Objects;
import java.util.UUID;

/** Same-thread persistence bridge. Only open after checking workspace membership. */
public final class WorkspaceScope implements AutoCloseable {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();
    private final UUID previous;
    private final Thread owner = Thread.currentThread();
    private boolean closed;

    private WorkspaceScope(UUID id) {
        previous = CURRENT.get();
        if (previous != null && !previous.equals(id)) {
            throw new IllegalStateException("Cannot switch workspace inside an active scope");
        }
        CURRENT.set(id);
    }

    public static WorkspaceScope open(UUID id) { return new WorkspaceScope(Objects.requireNonNull(id)); }
    public static UUID currentOrNull() { return CURRENT.get(); }
    public static UUID currentRequired() {
        UUID id = CURRENT.get();
        if (id == null) throw new IllegalStateException("Workspace scope is required for tenant SQL");
        return id;
    }

    @Override public void close() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Workspace scope must close on its owning thread");
        if (closed) return;
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        closed = true;
    }
}
