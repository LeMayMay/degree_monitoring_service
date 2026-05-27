package org.example.licenta_test_serverside_app;

import jakarta.ejb.Singleton; // <-- IMPORTANT: Changed from Stateless
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

@Path("/configs")
@Singleton
@Produces(MediaType.APPLICATION_JSON) // Applies JSON to all responses
@Consumes(MediaType.APPLICATION_JSON) // Applies JSON to all requests
public class PCConfigResource {

    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> pendingPings = new java.util.concurrent.ConcurrentHashMap<>();

    private volatile long globalPingExpirationTime = 0;

    // NEW: Stores the last 50 events in memory
    private final java.util.concurrent.ConcurrentLinkedDeque<String> eventLogs = new java.util.concurrent.ConcurrentLinkedDeque<>();

    @PersistenceContext(unitName = "default")
    private EntityManager em;

    @Context
    private Sse sse;
    private SseBroadcaster broadcaster;

    // Safely initialize the broadcaster when the first client connects
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
        // Register the browser's connection to our broadcaster
        broadcaster.register(eventSink);
    }

    // NEW: Replaces notifyClients(). Logs the event and broadcasts it as JSON.
    private void logEvent(String message) {
        String time = java.time.LocalTime.now().withNano(0).toString();
        String entry = "[" + time + "] " + message;

        eventLogs.addFirst(entry);
        if (eventLogs.size() > 50) {
            eventLogs.removeLast();
        }

        if (broadcaster != null && sse != null) {
            // Send both the refresh command AND the log message in one JSON payload
            String payload = "{\"action\":\"refresh\", \"log\":\"" + entry + "\"}";
            broadcaster.broadcast(sse.newEvent(payload));
        }
    }

    // NEW: Endpoint for the frontend to fetch logs on initial page load
    @GET
    @Path("/logs")
    public Response getEventLogs() {
        return Response.ok(eventLogs).build();
    }

    @POST
    public Response addConfig(PCConfig incomingConfig) {
        // Automatically attach the current timestamp to the payload
        incomingConfig.setLastUpdated(java.time.LocalDateTime.now());

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
            existing.setLastUpdated(java.time.LocalDateTime.now()); // Update timestamp

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
        return Response.ok().build();
    }

    @POST
    @Path("/ping/all")
    public Response triggerGlobalPing() {
        // Turn on the "Radio Broadcast" signal for 15 seconds.
        // Since clients check in every 10 seconds, 15 seconds guarantees
        // every single online client will hear it at least once.
        logEvent("Global Ping Broadcast Activated.");
        globalPingExpirationTime = System.currentTimeMillis() + 15000;

        return Response.ok("{\"message\": \"Global Ping Broadcast Activated for 15 seconds\"}").build();
    }

    @GET
    @Path("/tasks/{machineId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPendingTasks(@PathParam("machineId") String machineId) {

        // 1. Is the Global Radio Broadcast currently active?
        boolean hasGlobalPing = System.currentTimeMillis() < globalPingExpirationTime;

        // 2. Check for manual individual pings
        boolean hasManualPing = pendingPings.getOrDefault(machineId, false);

        // 3. Check for automatic 30-minute interval pings
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

        // If ANY of the three conditions are true, issue the PING instruction
        if (hasGlobalPing || hasManualPing || hasAutoPing) {
            // Clear the individual manual flag so we don't double-ping later
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

        return Response.noContent().build(); // 204 No Content is standard for a successful delete
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

        // 1. Basic empty check
        if (sql == null || sql.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Empty query provided.\"}")
                    .build();
        }

        // 2. SECURITY GATEKEEPER: Prevent destructive SQL Injection
        String upperSql = sql.toUpperCase().trim();

        // Rule A: The query MUST start with SELECT.
        if (!upperSql.startsWith("SELECT")) {
            logEvent("Blocked illegal SQL execution: Non-SELECT statement.");
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\": \"Security Policy: Only SELECT statements are allowed in this console.\"}")
                    .build();
        }

        // Rule B: Block stacked queries (using semicolons to chain commands) and destructive keywords
        // Note: We include spaces around the words so we don't accidentally block a PC hostname named "DROPLET"
        String[] forbiddenKeywords = {";", " DROP ", " DELETE ", " UPDATE ", " INSERT ", " ALTER ", " TRUNCATE ", " EXEC "};

        for (String keyword : forbiddenKeywords) {
            // We check if the query contains the keyword, OR ends with it
            if (upperSql.contains(keyword) || upperSql.endsWith(keyword.trim())) {
                logEvent("Blocked illegal SQL execution: Destructive keyword detected (" + keyword.trim() + ").");
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("{\"error\": \"Security Policy: Destructive commands or stacked queries are strictly prohibited.\"}")
                        .build();
            }
        }

        // 3. Safe Execution
        try {
            logEvent("Executed custom SQL query.");

            // Execute the raw query
            jakarta.persistence.Query query = em.createNativeQuery(sql);
            List<?> results = query.getResultList();

            // Let the server automatically map the List to a valid JSON array
            return Response.ok(results).build();

        } catch (Exception e) {
            // NEVER build JSON strings manually!
            // Put the error in a Map and let the server safely escape all newlines and quotes.
            java.util.Map<String, String> errorResponse = new java.util.HashMap<>();
            errorResponse.put("error", e.getMessage() != null ? e.getMessage() : e.toString());

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errorResponse)
                    .build();
        }
    }
}