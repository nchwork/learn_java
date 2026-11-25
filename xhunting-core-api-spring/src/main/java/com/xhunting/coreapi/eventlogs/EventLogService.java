package com.xhunting.coreapi.eventlogs;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

/** Thay cho {@code logPusher eventlogs.EventLogService}: ghi log ra console và giữ trong RAM. */
@Service
public class EventLogService {

    private static final System.Logger LOG = System.getLogger("eventlog");
    private final List<EventLog> logs = new CopyOnWriteArrayList<>(); // list an toàn đa luồng

    public void create(EventLog log) {
        logs.add(log);
        LOG.log(System.Logger.Level.INFO, "{0}", log);
    }

    public List<EventLog> all() {
        return List.copyOf(logs);
    }
}
