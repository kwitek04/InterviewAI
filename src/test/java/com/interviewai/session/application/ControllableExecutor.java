package com.interviewai.session.application;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

final class ControllableExecutor implements Executor {

    private final Queue<Runnable> pending = new ArrayDeque<>();

    @Override
    public void execute(Runnable command) {
        pending.add(command);
    }

    void runNext() {
        Runnable task = pending.poll();
        if (task != null) {
            task.run();
        }
    }

    void runAll() {
        Runnable task;
        while ((task = pending.poll()) != null) {
            task.run();
        }
    }

    boolean hasPending() {
        return !pending.isEmpty();
    }
}
