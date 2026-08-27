package com.flarelane;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
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

    /**
     * Add a task to the queue. If initialized, execute it immediately.
     *
     * Returns false when the SDK is stopped and the task was refused. Callers that owe the host app
     * a result must answer it themselves in that case — deciding here keeps the check and the
     * enqueue atomic, so a task can never be refused after a caller already saw "not stopped".
     */
    public void addTask(NamedRunnable task) {
        synchronized (this) {
            if (!isStopped) {
                taskQueue.add(task);
                Logger.verbose("Task added to queue: " + task.getTaskName() + ". Queue size after adding: " + taskQueue.size());

                if (isInitialized && !isProcessing) {
                    processNext();
                }
                return;
            }
        }

        // The decision was made under the lock and is final — this task was never queued, so nothing
        // can run it. Only the notification happens out here, because it hands control to host-app
        // code and must not run while the queue is locked.
        Logger.verbose("SDK is stopped, cancelling task: " + task.getTaskName());
        cancelSafely(task);
    }

    // Execute a task if not already processing another task.
    private synchronized void executeTask(NamedRunnable task) {
        if (isProcessing) return;
        isProcessing = true;
        Logger.verbose("Executing task: " + task.getTaskName() + ". Queue size before execution: " + taskQueue.size());

        // A timed-out task is NOT cancelled: its request may still be in flight and complete later,
        // which would answer the caller a second time. The React Native bridge is single-shot and
        // would crash on that. Advancing the queue is all we can safely do here — a caller whose
        // task times out is left waiting, which is a pre-existing gap tracked separately.
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
    public void stop() {
        List<NamedRunnable> cancelled;
        synchronized (this) {
            isStopped = true;
            cancelled = drainLocked();
        }

        Logger.verbose("SDK stopped, task queue cleared. Pending tasks cancelled: " + cancelled.size());
        for (NamedRunnable task : cancelled) {
            cancelSafely(task);
        }
    }

    /**
     * Empty the queue and cancel the running task's timeout. Call while holding the lock.
     *
     * A task already polled for execution is no longer in this queue, so exactly one of "runs" or
     * "is cancelled" happens for every task.
     */
    private List<NamedRunnable> drainLocked() {
        List<NamedRunnable> pending = new ArrayList<>(taskQueue);
        taskQueue.clear();

        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
        return pending;
    }

    /**
     * Answer a task that will never run. Host-app code runs here, so it is kept off the queue lock
     * and its exceptions are contained — one failing callback must not strand the others.
     */
    private void cancelSafely(NamedRunnable task) {
        try {
            task.cancel();
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }
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

    // Reset the task queue state and cancel all pending tasks
    public void reset() {
        List<NamedRunnable> cancelled;
        synchronized (this) {
            cancelled = drainLocked();
            isProcessing = false;
            isInitialized = false;
            isStopped = false;
        }

        // resetDevice() discards these too, so their callers are owed an answer just as much as on
        // stop(). Dropping them silently would hang a Flutter await the same way.
        Logger.verbose("Task queue reset completed. Pending tasks cancelled: " + cancelled.size());
        for (NamedRunnable task : cancelled) {
            cancelSafely(task);
        }
    }
}
