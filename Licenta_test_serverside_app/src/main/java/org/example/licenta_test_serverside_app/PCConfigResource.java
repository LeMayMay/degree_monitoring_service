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

    @PersistenceContext(unitName = "default")
    private EntityManager em;

    // --- NEW: SSE Components ---
    @Context
    private Sse sse;
    private SseBroadcaster broadcaster;

    // Safely initialize the broadcaster when the first client connects
    private synchronized void initBroadcaster() {
        if (broadcaster == null && sse != null) {
            broadcaster = sse.newBroadcaster();
        }
    }

    // --- NEW: The Subscription Endpoint for Browsers ---
    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void streamUpdates(@Context SseEventSink eventSink) {
        initBroadcaster();
        // Register the browser's connection to our broadcaster
        broadcaster.register(eventSink);
    }

    // --- NEW: Helper method to trigger UI refreshes ---
    private void notifyClients() {
        if (broadcaster != null && sse != null) {
            // Push a simple text event named "refresh" to all connected browsers
            broadcaster.broadcast(sse.newEvent("refresh"));
        }
    }

    @POST
    public Response addConfig(PCConfig incomingConfig) {
        List<PCConfig> existingList = em.createQuery(
                        "SELECT p FROM PCConfig p WHERE p.machineId = :mid", PCConfig.class)
                .setParameter("mid", incomingConfig.getMachineId())
                .getResultList();

        if (!existingList.isEmpty()) {
            PCConfig existing = existingList.get(0);
            PCConfigHistory historyEntry = new PCConfigHistory(existing);
            em.persist(historyEntry);

            existing.setHostname(incomingConfig.getHostname());
            existing.setCpuModel(incomingConfig.getCpuModel());
            existing.setRamGb(incomingConfig.getRamGb());
            existing.setStorage(incomingConfig.getStorage());
            existing.setGraphicsCards(incomingConfig.getGraphicsCards());
            existing.setOsVersion(incomingConfig.getOsVersion());
            existing.setAntivirus(incomingConfig.getAntivirus());

            em.merge(existing);

            notifyClients(); // <-- NEW: Push update to browsers

            return Response.ok(existing).build(); // 200 OK

        } else {
            em.persist(incomingConfig);
            notifyClients(); // <-- NEW: Push update to browsers
            return Response.status(Response.Status.CREATED).entity(incomingConfig).build(); // 201 Created
        }
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

        notifyClients(); // <-- NEW: Push update to browsers

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

        notifyClients(); // <-- NEW: Push update to browsers

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
    public Response executeSql(String sqlQuery) {
        try {
            String q = sqlQuery.trim().toUpperCase();
            if (q.startsWith("SELECT")) {
                List<?> results = em.createNativeQuery(sqlQuery).getResultList();
                return Response.ok(results).build();
            } else {
                int rowsAffected = em.createNativeQuery(sqlQuery).executeUpdate();
                return Response.ok("{\"rowsAffected\": " + rowsAffected + "}").build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }
}