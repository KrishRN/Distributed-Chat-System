package TASK.server;

import TASK.model.RemoteChatRoom;
import TASK.model.LocalChatRoom;
import TASK.model.RemoteUserInfo;
import TASK.model.UserInfo;
import TASK.service.ServerPriorityComparator;

import java.io.File;
import java.io.FileNotFoundException;
import java.rmi.Remote;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerState {
    private static ServerState instance;
    private ServerInfo serverInfo;
    private ConcurrentMap<String, LocalChatRoom> localChatRooms;
    private ConcurrentMap<String, RemoteChatRoom> remoteChatRooms;
    private ConcurrentMap<String, UserInfo> connectedClients;
    private ConcurrentMap<String, RemoteUserInfo> remoteClients;
    private ServerInfo coordinator;
    private AtomicBoolean ongoingElection;
    private ConcurrentMap<String, ServerInfo> serverInfoMap;
    private ConcurrentNavigableMap<String, ServerInfo> candidateServerInfoMap;
    private Map<String, ServerInfo> subordinateServerInfoMap;
    private ConcurrentMap<String, Integer> aliveMap;
    private AtomicBoolean stopRunning;
    private Long electionAnswerTimeout;
    private Long electionCoordinatorTimeout;

    private ServerState() {
        aliveMap = new ConcurrentHashMap<>();
        connectedClients = new ConcurrentHashMap<>();
        localChatRooms = new ConcurrentHashMap<>();
        remoteChatRooms = new ConcurrentHashMap<>();
        serverInfoMap = new ConcurrentHashMap<>();
        candidateServerInfoMap = new ConcurrentSkipListMap<>(new ServerPriorityComparator());
        subordinateServerInfoMap = new ConcurrentHashMap<>();
        ongoingElection = new AtomicBoolean(false);
        stopRunning = new AtomicBoolean(false);
    }
    public static synchronized ServerState getInstance() {
        if (instance == null) {
            instance = new ServerState();
        }
        return instance;
    }


    public synchronized ServerInfo getServerInfoById(String serverId) {
        return serverInfoMap.get(serverId);
    }

    public synchronized ServerInfo getServerInfo() {
        return serverInfo;
    }

    public synchronized List<ServerInfo> getServerInfoList() {
        //return serverInfoList;
        return new ArrayList<>(serverInfoMap.values());
    }

    public synchronized List<ServerInfo> getCandidateServerInfoList() {
        return new ArrayList<>(candidateServerInfoMap.values());
    }

    public synchronized List<ServerInfo> getSubordinateServerInfoList() {
        return new ArrayList<>(subordinateServerInfoMap.values());
    }

    public synchronized void addServer(ServerInfo serverInfo) {
        ServerInfo me = getServerInfo();
        if (null != serverInfo) {
            if (null != me) {
                if (new ServerPriorityComparator().compare(me.getServerId(), serverInfo.getServerId()) > 0) {
                    candidateServerInfoMap.put(serverInfo.getServerId(), serverInfo);
                } else if (new ServerPriorityComparator().compare(me.getServerId(), serverInfo.getServerId()) < 0) {
                    subordinateServerInfoMap.put(serverInfo.getServerId(), serverInfo);
                }
            }
            serverInfoMap.put(serverInfo.getServerId(), serverInfo);
        }

    }

    public synchronized void setupConnectedServers() {
        for (ServerInfo server : getServerInfoList()) {
            addServer(server);
        }
    }

    public synchronized void removeServer(String serverId) {
        serverInfoMap.remove(serverId);
    }

    public ConcurrentMap<String, Integer> getAliveMap() {
        return aliveMap;
    }


    public ConcurrentMap<String, LocalChatRoom> getLocalChatRooms() {
        return localChatRooms;
    }

    public ConcurrentMap<String, RemoteChatRoom> getRemoteChatRooms() {
        return remoteChatRooms;
    }

    public boolean isUserExisted(String userId) {
        return connectedClients.containsKey(userId) || remoteChatRooms.containsKey(userId) ;
    }

    public boolean isRoomExistedGlobally(String roomId) {
        return localChatRooms.containsKey(roomId) || remoteChatRooms.containsKey(roomId);
    }

    public boolean isRoomExistedLocally(String roomId) {
        return localChatRooms.containsKey(roomId);
    }

    public boolean isRoomExistedRemotely(String roomId) {
        return remoteChatRooms.containsKey(roomId);
    }

    public void stopRunning(boolean state) {
        stopRunning.set(state);
    }

    public boolean isStopRunning() {
        return stopRunning.get();
    }

    public synchronized ServerInfo getCoordinator() {
        return coordinator;
    }

    public synchronized void setCoordinator(ServerInfo coordinator) {
        addServer(coordinator);
        this.coordinator = coordinator;
    }


    public void setOngoingElection(boolean ongoingElection) {
        this.ongoingElection.set(ongoingElection);
    }

    public ConcurrentMap<String, UserInfo> getConnectedClients() {
        return connectedClients;
    }

    public ConcurrentMap<String, RemoteUserInfo> getRemoteClients() {
        return remoteClients;
    }

    public Long getElectionAnswerTimeout() {
        return electionAnswerTimeout;
    }

    public void setElectionAnswerTimeout(Long electionAnswerTimeout) {
        this.electionAnswerTimeout = electionAnswerTimeout;
    }

    public Long getElectionCoordinatorTimeout() {
        return electionCoordinatorTimeout;
    }

    public void setElectionCoordinatorTimeout(Long electionCoordinatorTimeout) {
        this.electionCoordinatorTimeout = electionCoordinatorTimeout;
    }

    public void removeRemoteChatRoomsByServerId(String serverId) {
        for (String entry : remoteChatRooms.keySet()) {
            RemoteChatRoom remoteChatRoom = remoteChatRooms.get(entry);
            if (remoteChatRoom.getManagingServer().equalsIgnoreCase(serverId)) {
                remoteChatRooms.remove(entry);
            }
        }
    }

    public void removeRemoteClientsByServerId(String serverId) {
        for (String entry : remoteClients.keySet()) {
            RemoteUserInfo remoteClient = remoteClients.get(entry);
            if (remoteClient.getManagingServer().equalsIgnoreCase(serverId)) {
                remoteClients.remove(entry);
            }
        }
    }
    public void initializeWithConfigs(String serverID, String serverConfPath) {
        serverInfo.setServerId(serverID);
        try {
            File conf = new File(serverConfPath); // read configuration
            Scanner myReader = new Scanner(conf);
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                String[] params = data.split(" ");
                if (params[0].equals(serverID)) {
                    serverInfo.setAddress(params[1]);
                    serverInfo.setPort(Integer.parseInt(params[2]));
                    serverInfo.setManagementPort(Integer.parseInt(params[3]));
                }
                // add all servers to hash map
                ServerInfo s = new ServerInfo((params[0]),params[1],
                        Integer.parseInt(params[3]),
                        Integer.parseInt(params[2])
                );
                serverInfoMap.put(s.getServerId(), s);
            }
            myReader.close();

        } catch (FileNotFoundException e) {
            System.out.println("Configs file not found");
            e.printStackTrace();
        }

    }

}
