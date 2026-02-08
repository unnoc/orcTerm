package com.orcterm.data;

/**
 * 主机状态数据模型
 */
public class HostStatus {
    public String cpuUsage = "0%";
    public int cpuUsagePercent = 0;
    public String memUsage = "0%";
    public int memUsagePercent = 0;
    public String netUpload = "0 B/s";
    public String netDownload = "0 B/s";
    public String diskRead = "0 B/s";
    public String diskWrite = "0 B/s";
    public String uptime = "-";
    public String cpuCores = "-";
    public String totalMem = "-";
    public String totalDisk = "-";
    public long latency = 0;
    public String temperature = null; // e.g. "45°C"
    public boolean isOnline = false;
    public long timestamp = 0;
}
