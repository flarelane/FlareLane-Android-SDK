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
        Logger.verbose("Adding task to queue: " + task.getTaskName() + ". Queue size before adding: " + taskQueue.size());
        if (!isInitialized) {
            taskQueue.add(task);
            Logger.verbose("Task added to queue: " + task.getTaskName() + ". Queue size after adding: " + taskQueue.size());
        } else {
            executeTask(task);
        }
    }

    // Execute a task if not already processing another task.
    public synchronized void executeTask(NamedRunnable task) {
        if (isProcessing) return;
        isProcessing = true;
        Logger.verbose("Executing task: " + task.getTaskName() + ". Queue size before execution: " + taskQueue.size());

        timeoutRunnable = () -> {
            synchronized (TaskQueueManager.this) {
                if (isProcessing) {
                    isProcessing = false;
                    Logger.verbose("Task timed out: " + task.getTaskName() + ". Processing next task.");
                    processNext();
                }
            }
        };
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS);

        // Set the task completion callback
        task.setTaskCompleteCallback(() -> {
            completeTask();
        });

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
    public synchronized void processNext() {
        if (taskQueue.isEmpty()) {
            isProcessing = false;
            Logger.verbose("No more tasks in queue. Queue is empty.");
            return;
        }

        NamedRunnable nextTask = taskQueue.poll();
        Logger.verbose("Processing next task: " + nextTask.getTaskName() + ". Queue size before processing: " + taskQueue.size());
        executeTask(nextTask);
        Logger.verbose("Next task processed: " + nextTask.getTaskName() + ". Queue size after processing: " + taskQueue.size());
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

    // Mark the task queue as initialized and start processing tasks.
    public synchronized void onInitialized() {
        isInitialized = true;
        Logger.verbose("Task queue initialized. Processing queued tasks.");
        processNext();
    }
}
