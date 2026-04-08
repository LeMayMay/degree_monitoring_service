package org.example;

import com.google.gson.Gson;
import oshi.SystemInfo;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OperatingSystem;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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

        //instantiating of oshi objects
        SystemInfo systemInfo = new SystemInfo();   //main entry point
        HardwareAbstractionLayer hardware = systemInfo.getHardware();   //hardware information is here
        OperatingSystem os = systemInfo.getOperatingSystem();           //OS information is here

        //isolating specifc information
        String hostname = os.getNetworkParams().getHostName();

        //cpu
        String cpuName = hardware.getProcessor().getProcessorIdentifier().getName().trim();
        int physCores = hardware.getProcessor().getPhysicalProcessorCount();
        int logCores = hardware.getProcessor().getLogicalProcessorCount();
        String cpuInfo = "CPU Name: " + cpuName + " Physical cores: " + physCores + " Logical cores: " + logCores;

        //RAM in GigaBytes
        long memoryBytes = hardware.getMemory().getTotal();
        int ramGb = (int) Math.round((double) memoryBytes / (1024 * 1024 * 1024));

        //Storage - Disk Drives
        List<HWDiskStore> diskStores = hardware.getDiskStores();
        String diskModel;
        String diskName;
        long diskSizeGB;
        StringBuilder disksInfo = new StringBuilder();

        //GPU
        List<GraphicsCard> cards = hardware.getGraphicsCards();
        String gpuName;
        StringBuilder gpuInfo = new StringBuilder();

        //OS name + version
        String osVersion = os.getFamily() + " " + os.getVersionInfo().getVersion();

        //Network adapters
        /*List<NetworkIF> networkInterfaces = hardware.getNetworkIFs();
        String netName;
        String netMacAddr;
        String[] ipv4Addr;
        StringBuilder networksInfo = new StringBuilder();*/

        //Antivirus Software Installed
        String foundApps = getDetectedAntivirus();

        Map<String, Object> configData = new HashMap<>();

        configData.put("hostname", hostname);

        configData.put("cpuModel", cpuInfo);

        configData.put("ramGb", ramGb);

        for (int i = 0; i < diskStores.size(); i++){
            diskModel = diskStores.get(i).getModel();
            diskName = diskStores.get(i).getName();
            diskSizeGB = diskStores.get(i).getSize() / (1024 * 1024 * 1024);
            String diskInfo = " Model: " + diskModel + " Size in GB: " + diskSizeGB;
            disksInfo.append(diskInfo);
        }
        configData.put("storage", disksInfo.toString());

        for (int i = 0; i < cards.size(); i++){
            gpuName = cards.get(i).getName();
            gpuInfo.append(gpuName + " ");
        }
        configData.put("graphicsCards", gpuInfo.toString());

        configData.put("osVersion", osVersion);

        /*for(int i = 0; i < networkInterfaces.size(); i++){
            if (networkInterfaces.get(i).getMacaddr().isEmpty() || networkInterfaces.get(i).getIPv4addr().length == 0) {
                continue;
            }
            netName = networkInterfaces.get(i).getDisplayName();
            netMacAddr = networkInterfaces.get(i).getMacaddr();
            ipv4Addr = networkInterfaces.get(i).getIPv4addr();
            String networkInfo = " Network name: " + netName + " Network MAC Address: " + netMacAddr + " Ipv4 Address: " + Arrays.toString(ipv4Addr);
            networksInfo.append(networkInfo);
        }
        configData.put("network", networksInfo.toString());*/

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
    public static String getDetectedAntivirus() {
        String os = System.getProperty("os.name").toLowerCase();
        String command;
        if (os.contains("win")) {
            // for windows:
            command = "powershell -command \"Get-ItemProperty HKLM:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*, " +
                    "HKLM:\\Software\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\* | " +
                    "Select-Object -ExpandProperty DisplayName -ErrorAction SilentlyContinue\"";
        } else if (os.contains("mac")) {
            // for macOS:
            command = "ls /Applications";
        } else {
            // for Linux:
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
                        break; // Move to next line once a match is found
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