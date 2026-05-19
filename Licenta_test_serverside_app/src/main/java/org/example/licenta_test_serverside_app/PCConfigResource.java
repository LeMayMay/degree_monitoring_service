package org.example.licenta_test_serverside_app;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/configs")
@Stateless
@Produces(MediaType.APPLICATION_JSON) // Applies JSON to all responses
@Consumes(MediaType.APPLICATION_JSON) // Applies JSON to all requests
public class PCConfigResource {

    @PersistenceContext(unitName = "default")
    private EntityManager em;

    @POST
    public Response addConfig(PCConfig incomingConfig) {
        // 1. Check if this specific PC already exists using the unique machineId
        List<PCConfig> existingList = em.createQuery(
                        "SELECT p FROM PCConfig p WHERE p.machineId = :mid", PCConfig.class)
                .setParameter("mid", incomingConfig.getMachineId())
                .getResultList();

        if (!existingList.isEmpty()) {
            // MACHINE EXISTS: Handle History and Update
            PCConfig existing = existingList.get(0);

            // Create a history record of the OLD state before we change it
            PCConfigHistory historyEntry = new PCConfigHistory(existing);
            em.persist(historyEntry);

            // Update the existing record with the new incoming data
            existing.setHostname(incomingConfig.getHostname());
            existing.setCpuModel(incomingConfig.getCpuModel());
            existing.setRamGb(incomingConfig.getRamGb());
            existing.setStorage(incomingConfig.getStorage());
            existing.setGraphicsCards(incomingConfig.getGraphicsCards());
            existing.setOsVersion(incomingConfig.getOsVersion());
            existing.setAntivirus(incomingConfig.getAntivirus());

            em.merge(existing);
            return Response.ok(existing).build(); // 200 OK

        } else {
            // MACHINE IS NEW: Insert it
            em.persist(incomingConfig);
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
        return Response.noContent().build(); // 204 No Content is standard for a successful delete
    }

// --- UPDATED: Get History by Machine ID ---
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

    // --- NEW: Raw SQL Execution ---
    @POST
    @Path("/query")
    @Consumes(MediaType.TEXT_PLAIN) // Accepts plain text SQL string
    public Response executeSql(String sqlQuery) {
        try {
            String q = sqlQuery.trim().toUpperCase();
            if (q.startsWith("SELECT")) {
                // Returns an array of objects for each row
                List<?> results = em.createNativeQuery(sqlQuery).getResultList();
                return Response.ok(results).build();
            } else {
                // Handles UPDATE, DELETE, INSERT
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