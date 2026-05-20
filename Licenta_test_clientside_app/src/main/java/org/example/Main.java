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

    private static final String SERVER_URL = "http://192.168.1.135:8080/Licenta_test_serverside_app-1.0-SNAPSHOT/api/configs";

    public static void main(String[] args) {
        System.out.println("PC Agent Daemon Started.");

        // 1. Send the initial payload on Startup
        sendPayload("STARTUP");

        // 2. Register a Shutdown Hook
        // This thread runs automatically when the OS sends a shutdown signal to the Java process
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown signal received. Sending final payload...");
            sendPayload("SHUTDOWN");
        }));

        // 3. Keep the Daemon Alive (The "Heartbeat" Loop)
        // This loop keeps the program running in the background invisibly.
        while (true) {
            try {
                // Sleep for 1 hour (3,600,000 ms).
                // In the future, we will change this to poll the server every few seconds for "Pings".
                Thread.sleep(3600000);

                // Optionally send a periodic heartbeat update
                // sendPayload("HEARTBEAT");

            } catch (InterruptedException e) {
                System.err.println("Daemon sleep interrupted.");
                break;
            }
        }
    }

    private static void sendPayload(String eventType) {
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
            String machineId = getOrGenerateMachineId();

            Map<String, Object> configData = new HashMap<>();
            configData.put("machineId", machineId);
            configData.put("hostname", hostname);
            configData.put("cpuModel", cpuInfo);
            configData.put("ramGb", ramGb);
            configData.put("storage", disksInfo.toString());
            configData.put("graphicsCards", gpuInfo.toString().trim());
            configData.put("osVersion", osVersion);
            configData.put("antivirus", foundApps);
            // You can also add configData.put("lastEvent", eventType); if you update your backend to accept it!

            Gson gson = new Gson();
            String jsonPayload = gson.toJson(configData);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10)) // Don't hang forever if server is down
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
            e.printStackTrace();
        }
    }

    private static String getOrGenerateMachineId() {
        File idFile = new File(System.getProperty("user.home"), ".pc_agent_id");
        try {
            if (idFile.exists()) {
                return new String(Files.readAllBytes(idFile.toPath())).trim();
            } else {
                String newId = UUID.randomUUID().toString();
                Files.write(idFile.toPath(), newId.getBytes());
                return newId;
            }
        } catch (Exception e) {
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