package com.flarelane;

import android.os.Handler;
import android.os.Looper;

import java.util.LinkedList;
import java.util.Queue;

public class TaskQueueManager {
    private static final long TIMEOUT_MS = 10000; // 10 seconds
    private final Queue<Runnable> taskQueue = new LinkedList<>();
    private boolean isProcessing = false;
    private boolean isInitialized = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;

    public synchronized void addTask(Runnable task) {
        if (!isInitialized) {
            taskQueue.add(task);
        } else {
            executeTask(task);
        }
    }

    public synchronized void executeTask(Runnable task) {
        if (isProcessing) return;
        isProcessing = true;

        timeoutRunnable = () -> {
            synchronized (TaskQueueManager.this) {
                isProcessing = false;
                processNext();
            }
        };
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS);

        task.run();
    }

    public synchronized void processNext() {
        if (taskQueue.isEmpty()) {
            isProcessing = false;
            return;
        }

        Runnable nextTask = taskQueue.poll();
        executeTask(nextTask);
    }

    public synchronized void onInitialized() {
        isInitialized = true;
        processNext();
    }

    public synchronized void onTaskComplete() {
        handler.removeCallbacks(timeoutRunnable);
        isProcessing = false;
        processNext();
    }
}
