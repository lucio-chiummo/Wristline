package app.wristline;

import java.util.ArrayDeque;

/** Runs one job at a time: offer() says whether to start now, done() hands out the next. Not thread-safe. */
final class SerialQueue<T> {
    private final ArrayDeque<T> waiting = new ArrayDeque<>();
    private boolean busy;

    /** The item to start now, or null when it has to wait for the running one. */
    T offer(T item) {
        if (busy) {
            waiting.add(item);
            return null;
        }
        busy = true;
        return item;
    }

    /** The running item finished: the next one to start, or null. */
    T done() {
        T next = waiting.poll();
        busy = next != null;
        return next;
    }
}
