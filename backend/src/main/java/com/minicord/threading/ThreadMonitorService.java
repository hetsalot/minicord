package com.minicord.threading;

import com.minicord.config.ServerConfig;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.*;

/**
 * Collects runtime JVM thread dump and thread pool metrics.
 * DC Topic 2: Multithreading
 */
@Service
public class ThreadMonitorService {

    private final ServerConfig serverConfig;
    private final ReplicationThreadPool threadPool;

    public ThreadMonitorService(ServerConfig serverConfig, ReplicationThreadPool threadPool) {
        this.serverConfig = serverConfig;
        this.threadPool = threadPool;
    }

    public Map<String, Object> getThreadDump() {
        Map<String, Object> data = new HashMap<>();
        data.put("serverId", serverConfig.getServerId());

        ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = mxBean.dumpAllThreads(false, false);

        List<Map<String, Object>> threadList = new ArrayList<>();
        for (ThreadInfo ti : threadInfos) {
            if (ti == null) continue;
            String name = ti.getThreadName();
            // Highlight our custom threads or include all
            Map<String, Object> t = new HashMap<>();
            t.put("name", name);
            t.put("state", ti.getThreadState().toString());
            t.put("daemon", ti.isDaemon());

            String info = "Thread ID: " + ti.getThreadId();
            if (name.startsWith("replication-pool")) {
                info = "Executing RMI replication tasks";
            } else if (name.startsWith("msg-processor")) {
                info = "Consuming messages from BlockingQueue";
            } else if (name.startsWith("ws-handler") || name.startsWith("http-nio")) {
                info = "Handling HTTP / WebSocket network traffic";
            } else if (name.contains("scheduling")) {
                info = "Heartbeat & failure detection timer";
            }
            t.put("info", info);
            threadList.add(t);
        }

        data.put("threads", threadList);

        Map<String, Object> poolStats = new HashMap<>();
        poolStats.put("name", "replication-pool");
        poolStats.put("coreSize", threadPool.getCoreSize());
        poolStats.put("maxSize", threadPool.getMaxSize());
        poolStats.put("activeCount", threadPool.getActiveCount());
        poolStats.put("queueSize", threadPool.getQueueSize());
        poolStats.put("completedTasks", threadPool.getCompletedTasks());
        poolStats.put("submittedTasks", threadPool.getSubmittedTasks());
        poolStats.put("availablePermits", threadPool.getAvailablePermits());

        data.put("threadPool", poolStats);
        return data;
    }
}
