package com.flarelane;

abstract class NamedRunnable implements Runnable {
    private final String taskName;
    private TaskCompleteCallback callback;

    public NamedRunnable(String taskName) {
        this.taskName = taskName;
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
