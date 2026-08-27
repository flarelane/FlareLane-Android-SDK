package com.flarelane;

import android.os.Handler;
import android.os.Looper;

import java.util.LinkedList;
import java.util.Queue;

class TaskQueueManager {
    private static final long TIMEOUT_MS = 10000; // 10 seconds
    private final Queue<NamedRunnable> taskQueue = new LinkedList<>();
    private boolean isProcessing = false;
    private boolean isInitialized = false;
    /** Set when the server tells the SDK to stop (HTTP 410). Nothing is queued or run afterwards. */
    private boolean isStopped = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;

    // Singleton instance
    private static TaskQueueManager instance;

    // Private constructor to prevent external instantiation
    private TaskQueueManager() {}

    // Method to obtain the singleton instance
    public static synchronized TaskQueueManager getInstance() {
        if (instance == null) {
            instance = new TaskQueueManager();
        }
        return instance;
    }

    // Add a task to the queue. If initialized, execute it immediately.
    public synchronized void addTask(NamedRunnable task) {
        if (isStopped) {
            Logger.verbose("SDK is stopped, ignoring task: " + task.getTaskName());
            return;
        }

        taskQueue.add(task);
        Logger.verbose("Task added to queue: " + task.getTaskName() + ". Queue size after adding: " + taskQueue.size());

        if (isInitialized && !isProcessing) {
            processNext();
        }
    }

    // Execute a task if not already processing another task.
    private synchronized void executeTask(NamedRunnable task) {
        if (isProcessing) return;
        isProcessing = true;
        Logger.verbose("Executing task: " + task.getTaskName() + ". Queue size before execution: " + taskQueue.size());

        timeoutRunnable = () -> {
            synchronized (TaskQueueManager.this) {
                if (isProcessing) {
                    Logger.verbose("Task timed out: " + task.getTaskName() + ". Processing next task.");
                    completeTask();
                }
            }
        };
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS);

        // Set the task completion callback
        task.setTaskCompleteCallback(this::completeTask);

        // Run the task
        new Thread(() -> {
            try {
                task.run();
            } catch (Exception e) {
                Logger.error("Error executing task: " + task.getTaskName());
                completeTask(); // Ensure completeTask is called even on error
            }
        }).start();
    }

    // Process the next task in the queue.
    private synchronized void processNext() {
        if (taskQueue.isEmpty()) {
            isProcessing = false;
            Logger.verbose("No more tasks in queue. Queue is empty.");
            return;
        }

        NamedRunnable nextTask = taskQueue.poll();
        Logger.verbose("Processing next task: " + nextTask.getTaskName() + ". Queue size before processing: " + taskQueue.size());
        executeTask(nextTask);
    }

    // Mark the current task as complete and process the next one.
    private synchronized void completeTask() {
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }

        isProcessing = false;
        Logger.verbose("Task completed. Queue size after completion: " + taskQueue.size());
        processNext();
    }

    /**
     * Drop everything pending and ignore new tasks, for the rest of this process.
     *
     * The queue only starts draining once the device is registered, which never happens when the
     * project is gone, so pending tasks would otherwise sit in memory for the app's lifetime.
     */
    public synchronized void stop() {
        isStopped = true;

        int discarded = taskQueue.size();
        taskQueue.clear();
        Logger.verbose("SDK stopped, task queue cleared. Pending tasks discarded: " + discarded);
    }

    synchronized boolean isStopped() {
        return isStopped;
    }

    /** Visible for tests: number of tasks still waiting. */
    synchronized int queueSize() {
        return taskQueue.size();
    }

    // Mark the task queue as initialized and start processing tasks.
    public synchronized void onInitialized() {
        isInitialized = true;
        Logger.verbose("Task queue initialized. Processing queued tasks.");
        if (!isProcessing) {
            processNext();
        }
    }

    // Reset the task queue state and clear all pending tasks
    public synchronized void reset() {
        Logger.verbose("Resetting task queue state");

        // Clear all pending tasks
        taskQueue.clear();

        // Reset processing state
        isProcessing = false;
        isInitialized = false;
        isStopped = false;

        // Cancel any pending timeout
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }

        Logger.verbose("Task queue reset completed");
    }
}
