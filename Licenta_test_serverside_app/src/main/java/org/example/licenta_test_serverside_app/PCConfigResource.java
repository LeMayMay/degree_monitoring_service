package org.example.licenta_test_serverside_app;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.ejb.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseBroadcaster;
import jakarta.ws.rs.sse.SseEventSink;
import java.util.List;


@DataSourceDefinition(
        name = "java:app/jdbc/pcInventoryDS",
        className = "org.h2.jdbcx.JdbcDataSource",
        url = "jdbc:h2:file:./database/pc_inventory_db;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE",
        user = "sa",
        password = ""
)
@Path("/configs")
@Singleton
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PCConfigResource {

    // Tracker for the exact millisecond a PC last checked in
    private final java.util.concurrent.ConcurrentHashMap<String, java.time.LocalDateTime> lastPingResponse = new java.util.concurrent.ConcurrentHashMap<>();
    // Background timer to handle the 20-second delays safely
    private final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newScheduledThreadPool(2);
    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> pendingPings = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile long globalPingExpirationTime = 0;
    private final java.util.concurrent.ConcurrentLinkedDeque<String> eventLogs = new java.util.concurrent.ConcurrentLinkedDeque<>();

    @PersistenceContext(unitName = "default")
    private EntityManager em;

    @Context
    private Sse sse;
    private SseBroadcaster broadcaster;

    private synchronized void initBroadcaster() {
        if (broadcaster == null && sse != null) {
            broadcaster = sse.newBroadcaster();
        }
    }

    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void streamUpdates(@Context SseEventSink eventSink) {
        initBroadcaster();
        broadcaster.register(eventSink);
    }

    private void logEvent(String message) {
        String time = java.time.LocalTime.now().withNano(0).toString();
        String entry = "[" + time + "] " + message;

        eventLogs.addFirst(entry);
        if (eventLogs.size() > 50) {
            eventLogs.removeLast();
        }

        if (broadcaster != null && sse != null) {
            String payload = "{\"action\":\"refresh\", \"log\":\"" + entry + "\"}";
            broadcaster.broadcast(sse.newEvent(payload));
        }
    }

    @GET
    @Path("/logs")
    public Response getEventLogs() {
        return Response.ok(eventLogs).build();
    }

    @POST
    public Response addConfig(PCConfig incomingConfig) {
        incomingConfig.setLastUpdated(java.time.LocalDateTime.now());
        // Log the exact time this payload arrived in high-speed memory for the ping tracker
        lastPingResponse.put(incomingConfig.getMachineId(), java.time.LocalDateTime.now());
        List<PCConfig> existingList = em.createQuery(
                        "SELECT p FROM PCConfig p WHERE p.machineId = :mid", PCConfig.class)
                .setParameter("mid", incomingConfig.getMachineId())
                .getResultList();

        if (!existingList.isEmpty()) {
            PCConfig existing = existingList.get(0);
            PCConfigHistory historyEntry = new PCConfigHistory(existing);
            em.persist(historyEntry);

            existing.setHostname(incomingConfig.getHostname());
            existing.setRoomName(incomingConfig.getRoomName());
            existing.setCpuModel(incomingConfig.getCpuModel());
            existing.setRamGb(incomingConfig.getRamGb());
            existing.setStorage(incomingConfig.getStorage());
            existing.setGraphicsCards(incomingConfig.getGraphicsCards());
            existing.setOsVersion(incomingConfig.getOsVersion());
            existing.setAntivirus(incomingConfig.getAntivirus());
            existing.setLastUpdated(java.time.LocalDateTime.now());

            em.merge(existing);
            logEvent("Saved configuration for PC: " + incomingConfig.getHostname());
            return Response.ok(existing).build();
        } else {
            em.persist(incomingConfig);
            logEvent("Saved configuration for PC: " + incomingConfig.getHostname());
            return Response.status(Response.Status.CREATED).entity(incomingConfig).build();
        }
    }

    @POST
    @Path("/ping/{machineId}")
    public Response triggerManualPing(@PathParam("machineId") String machineId) {
        logEvent("Queued manual ping for Agent ID: " + machineId);
        pendingPings.put(machineId, true);

        // 1. Record the exact time the ping was requested
        final java.time.LocalDateTime requestTime = java.time.LocalDateTime.now();

        // 2. Safely fetch the PC's hostname from the DB for the log message
        List<PCConfig> pcs = em.createQuery("SELECT p FROM PCConfig p WHERE p.machineId = :mid", PCConfig.class)
                .setParameter("mid", machineId)
                .getResultList();
        final String targetHostname = pcs.isEmpty() ? "Unknown PC" : pcs.get(0).getHostname();

        // 3. Start a background countdown for 20 seconds
        scheduler.schedule(() -> {
            java.time.LocalDateTime lastResponse = lastPingResponse.get(machineId);

            // If they never responded, or their last response is OLDER than our request...
            if (lastResponse == null || lastResponse.isBefore(requestTime)) {
                logEvent("⚠️ ALERT: " + targetHostname + " not responding to ping. Is the PC turned on?");
            } else {
                logEvent("✅ " + targetHostname + " successfully reported in.");
            }
        }, 20, java.util.concurrent.TimeUnit.SECONDS);

        return Response.ok().build();
    }

    @POST
    @Path("/ping/all")
    public Response triggerGlobalPing() {
        logEvent("Global Ping Broadcast Activated.");
        globalPingExpirationTime = System.currentTimeMillis() + 15000;

        final java.time.LocalDateTime requestTime = java.time.LocalDateTime.now();

        // Fetch all currently known PCs so the background thread knows who to check
        final List<PCConfig> allPcs = em.createQuery("SELECT p FROM PCConfig p", PCConfig.class).getResultList();

        // Start a background countdown for 20 seconds
        scheduler.schedule(() -> {
            java.util.List<String> ghosts = new java.util.ArrayList<>();

            for (PCConfig pc : allPcs) {
                java.time.LocalDateTime lastRes = lastPingResponse.get(pc.getMachineId());
                if (lastRes == null || lastRes.isBefore(requestTime)) {
                    ghosts.add(pc.getHostname());
                }
            }

            if (!ghosts.isEmpty()) {
                if (ghosts.size() == allPcs.size()) {
                    logEvent("⚠️ ALERT: No PCs responding!");
                } else {
                    logEvent("⚠️ ALERT: " + ghosts.size() + " PCs ignored the global ping. Missing: " + String.join(", ", ghosts));
                }
            } else {
                logEvent("✅ NETWORK SWEEP COMPLETE: Every single PC reported back successfully!");
            }
        }, 20, java.util.concurrent.TimeUnit.SECONDS);

        return Response.ok("{\"message\": \"Global Ping Broadcast Activated for 15 seconds\"}").build();
    }

    @GET
    @Path("/tasks/{machineId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPendingTasks(@PathParam("machineId") String machineId) {
        boolean hasGlobalPing = System.currentTimeMillis() < globalPingExpirationTime;
        boolean hasManualPing = pendingPings.getOrDefault(machineId, false);
        boolean hasAutoPing = false;

        List<PCConfig> pcs = em.createQuery("SELECT p FROM PCConfig p WHERE p.machineId = :mid", PCConfig.class)
                .setParameter("mid", machineId)
                .getResultList();

        if (!pcs.isEmpty() && pcs.get(0).getLastUpdated() != null) {
            long minutesElapsed = java.time.Duration.between(
                    pcs.get(0).getLastUpdated(),
                    java.time.LocalDateTime.now()
            ).toMinutes();

            if (minutesElapsed >= 30) {
                hasAutoPing = true;
            }
        }

        if (hasGlobalPing || hasManualPing || hasAutoPing) {
            pendingPings.put(machineId, false);
            return Response.ok("{\"command\": \"PING\"}").build();
        }

        return Response.ok("{\"command\": \"NONE\"}").build();
    }

    @GET
    public List<PCConfig> getAllConfigs() {
        return em.createQuery("SELECT p FROM PCConfig p", PCConfig.class).getResultList();
    }

    @PUT
    @Path("/{id}")
    public Response updateConfig(@PathParam("id") Long id, PCConfig updatedConfig) {
        PCConfig existing = em.find(PCConfig.class, id);
        if (existing == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        PCConfigHistory historyEntry = new PCConfigHistory(existing);
        em.persist(historyEntry);

        updatedConfig.setId(id);
        em.merge(updatedConfig);

        logEvent("Updated Configuration.");
        return Response.ok(updatedConfig).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteConfig(@PathParam("id") Long id) {
        PCConfig existing = em.find(PCConfig.class, id);
        if (existing == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        em.remove(existing);

        logEvent("Deleted a PC record from the database.");
        return Response.noContent().build();
    }

    @GET
    @Path("/history/{machineId}")
    public Response getHistory(@PathParam("machineId") String machineId) {
        List<PCConfigHistory> history = em.createQuery(
                        "SELECT h FROM PCConfigHistory h WHERE h.machineId = :machineId ORDER BY h.savedAt DESC",
                        PCConfigHistory.class)
                .setParameter("machineId", machineId)
                .getResultList();
        return Response.ok(history).build();
    }

    @POST
    @Path("/query")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response executeCustomQuery(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Empty query provided.\"}")
                    .build();
        }

        String upperSql = sql.toUpperCase().trim();

        if (!upperSql.startsWith("SELECT")) {
            logEvent("Blocked illegal SQL execution: Non-SELECT statement.");
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\": \"Security Policy: Only SELECT statements are allowed in this console.\"}")
                    .build();
        }

        String[] forbiddenKeywords = {";", " DROP ", " DELETE ", " UPDATE ", " INSERT ", " ALTER ", " TRUNCATE ", " EXEC "};

        for (String keyword : forbiddenKeywords) {
            if (upperSql.contains(keyword) || upperSql.endsWith(keyword.trim())) {
                logEvent("Blocked illegal SQL execution: Destructive keyword detected (" + keyword.trim() + ").");
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("{\"error\": \"Security Policy: Destructive commands or stacked queries are strictly prohibited.\"}")
                        .build();
            }
        }

        try {
            logEvent("Executed custom SQL query.");
            jakarta.persistence.Query query = em.createNativeQuery(sql);
            List<?> results = query.getResultList();
            return Response.ok(results).build();
        } catch (Exception e) {
            java.util.Map<String, String> errorResponse = new java.util.HashMap<>();
            errorResponse.put("error", e.getMessage() != null ? e.getMessage() : e.toString());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errorResponse)
                    .build();
        }
    }
}