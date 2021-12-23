package TASK.server;

import java.util.Objects;

public class ServerInfo {

    private String serverId;

    private String address;

    private Integer clientPort;

    private Integer serverPort;

    public ServerInfo() {
    }

    public ServerInfo(String serverName, String address, Integer clientPort, Integer serverPort) {
        this.serverId = serverName;
        this.address = address;
        this.clientPort = clientPort;
        this.serverPort = serverPort;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Integer getClientPort() {
        return clientPort;
    }

    public void setPort(Integer port) {
        this.clientPort = port;
    }

    public Integer getServerPort() {
        return serverPort;
    }

    public void setManagementPort(Integer port) {
        this.serverPort = port;
    }

    @Override
    public boolean equals(Object other) {
        boolean result = false;
        if (other instanceof ServerInfo) {
            ServerInfo that = (ServerInfo) other;
            result = (Objects.equals(this.getServerId(), that.getServerId())
                    && Objects.equals(this.getClientPort(), that.getClientPort())
                    && Objects.equals(this.getServerPort(), that.getServerPort())
            );
        }
        return result;
    }
}