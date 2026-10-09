package com.minicord.threading;

import com.minicord.model.Message;
import com.minicord.storage.MessageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * Dedicated consumer thread processing incoming messages from a BlockingQueue.
 * DC Topic 2: Multithreading (Producer-Consumer pattern with BlockingQueue)
 */
@Component
public class MessageProcessorThread implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(MessageProcessorThread.class);

    private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>(1000);
    private final MessageStore messageStore;
    private volatile boolean running = true;
    private Thread workerThread;

    private Consumer<Message> onMessageProcessedCallback;

    public MessageProcessorThread(MessageStore messageStore) {
        this.messageStore = messageStore;
    }

    public void setOnMessageProcessedCallback(Consumer<Message> callback) {
        this.onMessageProcessedCallback = callback;
    }

    @PostConstruct
    public void start() {
        workerThread = new Thread(this, "msg-processor");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("MessageProcessorThread started on daemon thread: msg-processor");
    }

    public boolean enqueue(Message message) {
        return messageQueue.offer(message);
    }

    public int getQueueSize() {
        return messageQueue.size();
    }

    @Override
    public void run() {
        while (running) {
            try {
                // Blocks until a message is available
                Message msg = messageQueue.take();
                messageStore.saveMessage(msg);

                if (onMessageProcessedCallback != null) {
                    onMessageProcessedCallback.accept(msg);
                }
            } catch (InterruptedException e) {
                if (!running) {
                    break;
                }
                Thread.currentThread().interrupt();
                log.warn("MessageProcessorThread interrupted");
            } catch (Exception e) {
                log.error("Error processing message in background worker", e);
            }
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }
}
