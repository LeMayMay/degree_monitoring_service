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
public class PCConfigResource {

    @PersistenceContext(unitName = "default")
    private EntityManager em;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addConfig(PCConfig config) {
        em.persist(config);
        // Returning the config so the frontend gets the newly generated ID
        return Response.status(Response.Status.CREATED).entity(config).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<PCConfig> getAllConfigs() {
        return em.createQuery("SELECT p FROM PCConfig p", PCConfig.class).getResultList();
    }

    // --- NEW: UPDATE an existing configuration ---
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateConfig(@PathParam("id") Long id, PCConfig updatedConfig) {
        // 1. Check if the PC exists in the database
        PCConfig existing = em.find(PCConfig.class, id);

        if (existing == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // 2. Ensure the ID from the URL is applied to the incoming object
        updatedConfig.setId(id);

        // 3. Merge the updates into the database
        em.merge(updatedConfig);

        return Response.ok(updatedConfig).build();
    }

    // --- NEW: DELETE a configuration ---
    @DELETE
    @Path("/{id}")
    public Response deleteConfig(@PathParam("id") Long id) {
        // 1. Find the PC to delete
        PCConfig existing = em.find(PCConfig.class, id);

        if (existing == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // 2. Remove it from the database
        em.remove(existing);

        return Response.noContent().build(); // 204 No Content is standard for a successful delete
    }
}