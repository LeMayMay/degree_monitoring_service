package org.example;

import com.google.gson.Gson;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {

        System.out.println("Gathering system information...");

        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hardware = systemInfo.getHardware();
        OperatingSystem os = systemInfo.getOperatingSystem();

        String hostname = os.getNetworkParams().getHostName();
        String cpuModel = hardware.getProcessor().getProcessorIdentifier().getName().trim();

        long memoryBytes = hardware.getMemory().getTotal();
        int ramGb = (int) Math.round((double) memoryBytes / (1024 * 1024 * 1024));

        String osVersion = os.getFamily() + " " + os.getVersionInfo().getVersion();

        Map<String, Object> configData = new HashMap<>();
        configData.put("hostname", hostname);
        configData.put("cpuModel", cpuModel);
        configData.put("ramGb", ramGb);
        configData.put("osVersion", osVersion);

        Gson gson = new Gson();
        String jsonPayload = gson.toJson(configData);

        System.out.println("Generated JSON Payload: \n" + jsonPayload);

        String targetUrl = "http://192.168.1.135:8080/Licenta_test_serverside_app-1.0-SNAPSHOT/api/configs";

        try {
            System.out.println("Sending data to server...");
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                System.out.println("Success! Server saved the configuration.");
            } else {
                System.out.println("Server returned status code: " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("Failed to send data. Ensure the server is running.");
            e.printStackTrace();
        }
    }
}