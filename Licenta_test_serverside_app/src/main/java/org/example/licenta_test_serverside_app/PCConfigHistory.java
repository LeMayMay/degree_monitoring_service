package org.example.licenta_test_serverside_app;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pc_configs_history")
public class PCConfigHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    private Long originalId;
    private String hostname;
    private String cpuModel;
    private Integer ramGb;
    private String storage;
    private String graphicsCards;
    private String osVersion;
    private String antivirus;


    @Column(name = "saved_at")
    private LocalDateTime savedAt;

    @Column(name = "machine_id")
    private String machineId;

    @Column(name = "room_name")
    private String roomName;

    // Default constructor
    public PCConfigHistory() {}

    // Constructor to quickly clone an existing PCConfig
    public PCConfigHistory(PCConfig config) {
        this.originalId = config.getId();
        this.machineId = config.getMachineId();
        this.hostname = config.getHostname();
        this.cpuModel = config.getCpuModel();
        this.ramGb = config.getRamGb();
        this.storage = config.getStorage();
        this.graphicsCards = config.getGraphicsCards();
        this.osVersion = config.getOsVersion();
        this.antivirus = config.getAntivirus();
        this.savedAt = LocalDateTime.now();
        this.roomName = config.getRoomName();
    }

    // Getters and Setters
    public Long getHistoryId() { return historyId; }
    public void setHistoryId(Long historyId) { this.historyId = historyId; }
    public Long getOriginalId() { return originalId; }
    public void setOriginalId(Long originalId) { this.originalId = originalId; }
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }
    public String getCpuModel() { return cpuModel; }
    public void setCpuModel(String cpuModel) { this.cpuModel = cpuModel; }
    public Integer getRamGb() { return ramGb; }
    public void setRamGb(Integer ramGb) { this.ramGb = ramGb; }
    public String getStorage() { return storage; }
    public void setStorage(String storage) { this.storage = storage; }
    public String getGraphicsCards() { return graphicsCards; }
    public void setGraphicsCards(String graphicsCards) { this.graphicsCards = graphicsCards; }
    public String getOsVersion() { return osVersion; }
    public void setOsVersion(String osVersion) { this.osVersion = osVersion; }
    public String getAntivirus() { return antivirus; }
    public void setAntivirus(String antivirus) { this.antivirus = antivirus; }
    public LocalDateTime getSavedAt() { return savedAt; }
    public void setSavedAt(LocalDateTime savedAt) { this.savedAt = savedAt; }
    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
}