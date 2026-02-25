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
    public Response addConfig(PCConfig config) {
        em.persist(config);
        return Response.status(Response.Status.CREATED).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<PCConfig> getAllConfigs() {
        return em.createQuery("SELECT p FROM PCConfig p", PCConfig.class).getResultList();
    }
}