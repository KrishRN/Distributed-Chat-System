package TASK.model;

public class RemoteUserInfo {
    private String identity;
    private String managingServer;

    public RemoteUserInfo(String identity, String managingServer) {
        this.identity = identity;
        this.managingServer = managingServer;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public String getManagingServer() {
        return managingServer;
    }

    public void setManagingServer(String managingServer) {
        this.managingServer = managingServer;
    }
}
