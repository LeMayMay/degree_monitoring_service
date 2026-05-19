package org.example.licenta_test_serverside_app;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.*;
@Entity
public class PCConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String hostname;
    private String cpuModel;
    private int ramGb;
    private String storage;
    private String graphicsCards;
    private String osVersion;
    private String network;
    private String antivirus;
    private String machineId;

    @Column(name = "room_name")
    private String roomName;

    public PCConfig() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public String getCpuModel() { return cpuModel; }
    public void setCpuModel(String cpuModel) { this.cpuModel = cpuModel; }

    public int getRamGb() { return ramGb; }
    public void setRamGb(int ramGb) { this.ramGb = ramGb; }

    public String getStorage() { return storage; }
    public void setStorage(String storage) { this.storage = storage; }

    public String getGraphicsCards() { return graphicsCards; }
    public void setGraphicsCards(String graphicsCards) { this.graphicsCards = graphicsCards; }

    public String getOsVersion() { return osVersion; }
    public void setOsVersion(String osVersion) { this.osVersion = osVersion; }

    public String getNetwork() { return network; }
    public void setNetwork(String network) { this.network = network; }

    public String getAntivirus() { return antivirus; }
    public void setAntivirus(String antivirus) { this.antivirus = antivirus; }

    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
}
