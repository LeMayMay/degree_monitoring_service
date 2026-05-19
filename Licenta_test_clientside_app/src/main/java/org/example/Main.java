package org.example;

import com.google.gson.Gson;
import oshi.SystemInfo;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class Main {
    private static final Set<String> AV_KEYWORDS = new HashSet<>(Arrays.asList(
            "malwarebytes", "bitdefender", "norton", "mcafee", "avast",
            "avg", "eset", "kaspersky", "sophos", "sentinelone",
            "crowdstrike", "trend micro", "avira", "totalav", "defender",
            "f-secure", "bullguard", "webroot", "panda security", "intego"
    ));

    public static void main(String[] args) {
        System.out.println("Gathering system information...");

        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hardware = systemInfo.getHardware();
        OperatingSystem os = systemInfo.getOperatingSystem();

        String hostname = os.getNetworkParams().getHostName();

        // CPU
        String cpuName = hardware.getProcessor().getProcessorIdentifier().getName().trim();
        int physCores = hardware.getProcessor().getPhysicalProcessorCount();
        int logCores = hardware.getProcessor().getLogicalProcessorCount();
        String cpuInfo = "CPU Name: " + cpuName + " Physical cores: " + physCores + " Logical cores: " + logCores;

        // RAM
        long memoryBytes = hardware.getMemory().getTotal();
        int ramGb = (int) Math.round((double) memoryBytes / (1024 * 1024 * 1024));

        // Storage
        List<HWDiskStore> diskStores = hardware.getDiskStores();
        StringBuilder disksInfo = new StringBuilder();
        for (HWDiskStore disk : diskStores) {
            long diskSizeGB = disk.getSize() / (1024 * 1024 * 1024);
            disksInfo.append(" Model: ").append(disk.getModel()).append(" Size in GB: ").append(diskSizeGB);
        }

        // GPU
        List<GraphicsCard> cards = hardware.getGraphicsCards();
        StringBuilder gpuInfo = new StringBuilder();
        for (GraphicsCard card : cards) {
            gpuInfo.append(card.getName()).append(" ");
        }

        // OS & Antivirus
        String osVersion = os.getFamily() + " " + os.getVersionInfo().getVersion();
        String foundApps = getDetectedAntivirus();

        // Get or Generate the Unique Machine ID
        String machineId = getOrGenerateMachineId();

        // Build Payload
        Map<String, Object> configData = new HashMap<>();
        configData.put("machineId", machineId); // <-- NEW FIELD ADDED HERE
        configData.put("hostname", "REZE");
        configData.put("cpuModel", cpuInfo);
        configData.put("ramGb", ramGb);
        configData.put("storage", disksInfo.toString());
        configData.put("graphicsCards", gpuInfo.toString().trim());
        configData.put("osVersion", osVersion);
        configData.put("antivirus", foundApps);

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

            if (response.statusCode() == 201 || response.statusCode() == 200) {
                System.out.println("Success! Server saved the configuration.");
                JOptionPane.showMessageDialog(null,"Success! Server saved the configuration.","Data sent",1);
            } else {
                System.out.println("Server returned status code: " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("Failed to send data. Ensure the server is running.");
            e.printStackTrace();
        }
    }

    // --- NEW METHOD: Handles the permanent unique key ---
    private static String getOrGenerateMachineId() {
        File idFile = new File(System.getProperty("user.home"), ".pc_agent_id");
        try {
            if (idFile.exists()) {
                // Read existing ID
                return new String(Files.readAllBytes(idFile.toPath())).trim();
            } else {
                // Generate new UUID and save it
                String newId = UUID.randomUUID().toString();
                Files.write(idFile.toPath(), newId.getBytes());
                return newId;
            }
        } catch (Exception e) {
            // Fallback in case of file permission issues
            System.err.println("Could not read/write agent ID file. Generating temporary ID.");
            return UUID.randomUUID().toString();
        }
    }

    public static String getDetectedAntivirus() {
        // ... (keep your existing implementation) ...
        String os = System.getProperty("os.name").toLowerCase();
        String command;
        if (os.contains("win")) {
            command = "powershell -command \"Get-ItemProperty HKLM:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*, " +
                    "HKLM:\\Software\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\* | " +
                    "Select-Object -ExpandProperty DisplayName -ErrorAction SilentlyContinue\"";
        } else if (os.contains("mac")) {
            command = "ls /Applications";
        } else {
            command = "dpkg-query -W -f=${Package}\\n";
        }
        return filterResults(command);
    }

    private static String filterResults(String command) {
        // ... (keep your existing implementation) ...
        StringBuilder filteredOutput = new StringBuilder();
        try {
            String[] cmdArray = System.getProperty("os.name").toLowerCase().contains("win")
                    ? new String[]{"powershell", "-command", command.replace("powershell -command ", "").replace("\"", "")}
                    : new String[]{"/bin/sh", "-c", command};

            Process process = Runtime.getRuntime().exec(cmdArray);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                String lowerLine = line.toLowerCase().trim();
                for (String av : AV_KEYWORDS) {
                    if (lowerLine.contains(av)) {
                        filteredOutput.append(line).append("\n");
                        break;
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            return "Error during scan: " + e.getMessage();
        }
        return filteredOutput.toString();
    }
}