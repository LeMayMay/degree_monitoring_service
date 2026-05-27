package org.example.licenta_test_serverside_app;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

@Path("/auth")
@Singleton
@Startup
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.TEXT_PLAIN) // We will accept the password as plain text
public class AuthResource {

    private String currentPassword;
    private final String FILE_PATH = "./database/admin_password.txt";

    @PostConstruct
    public void init() {
        try {
            // Ensure the database directory exists
            File dir = new File("./database");
            if (!dir.exists()) dir.mkdirs();

            File authFile = new File(FILE_PATH);

            if (authFile.exists()) {
                // Read the existing password
                currentPassword = new String(Files.readAllBytes(Paths.get(FILE_PATH))).trim();
                System.out.println("[AUTH] Loaded existing unique admin password from hard drive.");
            } else {
                // Generate a random 8-character alphanumeric password for this specific server
                currentPassword = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                Files.write(Paths.get(FILE_PATH), currentPassword.getBytes());

                // Print a massive banner so the admin sees it on first boot
                System.out.println("\n=======================================================");
                System.out.println("🚨 NEW SERVER DEPLOYMENT DETECTED 🚨");
                System.out.println("Your unique Admin Password has been generated:");
                System.out.println("PASSWORD:  " + currentPassword);
                System.out.println("Saved to:  " + authFile.getAbsolutePath());
                System.out.println("=======================================================\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @POST
    @Path("/login")
    public Response login(String attemptedPassword) {
        if (attemptedPassword != null && attemptedPassword.trim().equals(currentPassword)) {
            // Success! Return a simple token.
            return Response.ok("{\"status\": \"success\"}").build();
        }
        // Failure! Return a 401 Unauthorized status.
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity("{\"error\": \"Invalid password\"}")
                .build();
    }
}