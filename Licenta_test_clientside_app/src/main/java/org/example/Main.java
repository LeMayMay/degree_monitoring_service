package org.example;

import com.google.gson.Gson;
import oshi.SystemInfo;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class Main {
    private static final Set<String> AV_KEYWORDS = new HashSet<>(Arrays.asList(
            "malwarebytes", "bitdefender", "norton", "mcafee", "avast",
            "avg", "eset", "kaspersky", "sophos", "sentinelone",
            "crowdstrike", "trend micro", "avira", "totalav", "defender",
            "f-secure", "bullguard", "webroot", "panda security", "intego"
    ));

    private static final String SERVER_URL = "http://192.168.1.133:8080/Licenta_test_serverside_app-1.0-SNAPSHOT/api/configs";

    public static void main(String[] args) {
        // 1. Fetch the Machine ID once at startup
        String machineId = getOrGenerateMachineId();

        // 2. Check for Native OS Triggers via Command Line Arguments
        if (args.length > 0) {
            String trigger = args[0].toUpperCase();
            if (trigger.equals("--BOOT")) {
                System.out.println("OS Boot Trigger intercepted.");
                sendPayload("OS_BOOT", machineId);
                return; // Exit immediately, the native background service will handle polling
            } else if (trigger.equals("--SHUTDOWN")) {
                System.out.println("OS Shutdown Trigger intercepted.");
                sendPayload("OS_SHUTDOWN", machineId);
                return; // Exit immediately so the OS can finish powering off
            }
        }

        // 3. Fallback: Standard continuous background daemon mode
        System.out.println("PC Agent Daemon Started in Polling Mode.");
        sendPayload("STARTUP", machineId);

        // Standard continuous loop (keeps running indefinitely in the background)
        while (true) {
            try {
                Thread.sleep(10000);
                checkForTasks(machineId);
            } catch (InterruptedException e) {
                System.err.println("Daemon execution interrupted by OS. Exiting...");
                break;
            } catch (Exception e) {
                System.err.println("[WARNING] Background loop encountered an error, retrying next cycle...");
            }
        }
    }

    // --- UPDATED: Now accepts machineId as a parameter ---
    private static void sendPayload(String eventType, String machineId) {
        try {
            System.out.println("[" + eventType + "] Gathering system information...");

            SystemInfo systemInfo = new SystemInfo();
            HardwareAbstractionLayer hardware = systemInfo.getHardware();
            OperatingSystem os = systemInfo.getOperatingSystem();

            String hostname = os.getNetworkParams().getHostName();

            String cpuName = hardware.getProcessor().getProcessorIdentifier().getName().trim();
            int physCores = hardware.getProcessor().getPhysicalProcessorCount();
            int logCores = hardware.getProcessor().getLogicalProcessorCount();
            String cpuInfo = "CPU Name: " + cpuName + " Physical cores: " + physCores + " Logical cores: " + logCores;

            long memoryBytes = hardware.getMemory().getTotal();
            int ramGb = (int) Math.round((double) memoryBytes / (1024 * 1024 * 1024));

            List<HWDiskStore> diskStores = hardware.getDiskStores();
            StringBuilder disksInfo = new StringBuilder();
            for (HWDiskStore disk : diskStores) {
                long diskSizeGB = disk.getSize() / (1024 * 1024 * 1024);
                disksInfo.append(" Model: ").append(disk.getModel()).append(" Size in GB: ").append(diskSizeGB);
            }

            List<GraphicsCard> cards = hardware.getGraphicsCards();
            StringBuilder gpuInfo = new StringBuilder();
            for (GraphicsCard card : cards) {
                gpuInfo.append(card.getName()).append(" ");
            }

            String osVersion = os.getFamily() + " " + os.getVersionInfo().getVersion();
            String foundApps = getDetectedAntivirus();

            Map<String, Object> configData = new HashMap<>();
            configData.put("machineId", machineId); // Uses the parameter passed in
            configData.put("hostname", hostname);
            configData.put("cpuModel", cpuInfo);
            configData.put("ramGb", ramGb);
            configData.put("storage", disksInfo.toString());
            configData.put("graphicsCards", gpuInfo.toString().trim());
            configData.put("osVersion", osVersion);
            configData.put("antivirus", foundApps);

            Gson gson = new Gson();
            String jsonPayload = gson.toJson(configData);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SERVER_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("[" + eventType + "] Server response: " + response.statusCode());

        } catch (Exception e) {
            System.err.println("[" + eventType + "] Failed to send payload.");
            // e.printStackTrace(); // Optional: comment out to prevent spamming the console on network loss
        }
    }

    private static void checkForTasks(String machineId) {
        try {
            String taskUrl = SERVER_URL + "/tasks/" + machineId;

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5)) // Don't hang if server is down
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(taskUrl))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Gson gson = new Gson();
                Map<?, ?> taskData = gson.fromJson(response.body(), Map.class);
                String command = (String) taskData.get("command");

                if ("PING".equals(command)) {
                    System.out.println("[COMMAND] Ping request intercepted from server. Transmitting fresh inventory data...");
                    // Transmit data and pass the machineId
                    sendPayload("PING_RESPONSE", machineId);
                }
            }
        } catch (Exception e) {
            // Silently suppress network errors
            // System.err.println("Failed to reach task queue: " + e.getMessage());
        }
    }

    // --- UPGRADED METHOD: Uses Physical Motherboard UUID ---
    private static String getOrGenerateMachineId() {
        try {
            SystemInfo systemInfo = new SystemInfo();
            String hwUUID = systemInfo.getHardware().getComputerSystem().getHardwareUUID();

            // Check if the motherboard returned a valid UUID (not empty, "unknown", or all F's)
            if (hwUUID != null && !hwUUID.trim().isEmpty()
                    && !hwUUID.equalsIgnoreCase("unknown")
                    && !hwUUID.contains("FFFFFFFF")) {

                return hwUUID; // Use the physical hardware ID
            }
        } catch (Exception e) {
            System.err.println("Could not read hardware UUID. Falling back to file generation.");
        }

        // FALLBACK: Only used if hardware UUID fails (e.g., on some Virtual Machines)
        File idFile = new File(".pc_agent_id");
        try {
            if (idFile.exists()) {
                return new String(Files.readAllBytes(idFile.toPath())).trim();
            } else {
                String newId = UUID.randomUUID().toString();
                Files.write(idFile.toPath(), newId.getBytes());
                return newId;
            }
        } catch (Exception e) {
            System.err.println("Could not read/write agent ID file. Generating temporary ID.");
            return UUID.randomUUID().toString();
        }
    }

    public static String getDetectedAntivirus() {
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