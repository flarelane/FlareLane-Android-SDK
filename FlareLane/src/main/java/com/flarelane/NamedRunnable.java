package com.flarelane;

import androidx.annotation.Nullable;

abstract class NamedRunnable implements Runnable {
    private final String taskName;
    private final Runnable onCancelled;
    private TaskCompleteCallback callback;

    public NamedRunnable(String taskName) {
        this(taskName, null);
    }

    /**
     * @param onCancelled run instead of {@link #run()} when the task will never run because the SDK
     *     stopped or was reset. Tasks that owe the host app a result must pass one — a Flutter
     *     `await` or a React Native callback resolves only from inside the task body, so dropping it
     *     silently leaves the host app waiting forever.
     */
    public NamedRunnable(String taskName, @Nullable Runnable onCancelled) {
        this.taskName = taskName;
        this.onCancelled = onCancelled;
    }

    /** Called by the queue when this task is discarded or refused without running. */
    void cancel() {
        if (onCancelled != null) onCancelled.run();
    }

    public String getTaskName() {
        return taskName;
    }

    // Set the callback for task completion
    public void setTaskCompleteCallback(TaskCompleteCallback callback) {
        this.callback = callback;
    }

    // Method to notify task completion
    protected void completeTask() {
        if (callback != null) {
            callback.onComplete();
        }
    }

    // Run method to be implemented by subclasses
    @Override
    public abstract void run();

    // Interface for task completion callback
    public interface TaskCompleteCallback {
        void onComplete();
    }
}
