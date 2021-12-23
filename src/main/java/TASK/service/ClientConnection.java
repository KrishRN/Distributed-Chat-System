package TASK.service;

import TASK.model.LocalChatRoom;
import TASK.model.RemoteChatRoom;
import TASK.model.UserInfo;
import TASK.server.ServerInfo;
import TASK.server.ServerState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientConnection implements Runnable {

    private BufferedReader reader;
    private BufferedWriter writer;
    private JSONParser parser;
    private JSONBuilder messageBuilder;
    private ServerCommunication serverCommunication;
    private ClientCommunication clientCommunication;

    private Socket clientSocket;
    private UserInfo userInfo;
    private boolean routed = false;

    private ServerState serverState = ServerState.getInstance();

    public ClientConnection(Socket clientSocket) {
        try {
            this.clientSocket = clientSocket;
            this.reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
            this.writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"));
            this.parser = new JSONParser();
            this.messageBuilder = JSONBuilder.getInstance();
            this.serverCommunication = new ServerCommunication();
            this.clientCommunication = new ClientCommunication();
        } catch (Exception e) {
            logger.trace(e.getMessage());
        }
    }

    @Override
    public void run() {

        try {

            while (true) {

                String msg = reader.readLine();

                // convert received message to json object
                JSONObject jsonMessage = (JSONObject) parser.parse(msg);
                logger.debug("Receiving: " + msg);


                String type = (String) jsonMessage.get("type");

                if (type.equalsIgnoreCase("list")){
                    write(messageBuilder.listRooms());
                }
                else if (type.equalsIgnoreCase("newidentity")) {

                    String mainHall = "MainHall-" + serverState.getServerInfo().getServerId();
                    String requestIdentity = (String) jsonMessage.get("identity");

                    boolean isUserExisted = serverState.isUserExisted(requestIdentity);
                    boolean isIdValid = Utilities.isIdValid(requestIdentity);

                    if (isUserExisted || !isIdValid) {
                        // {"type" : "newidentity", "approved" : "false"}
                        this.write(messageBuilder.newIdentityResp("false"));
                    } else {

                        boolean approved = serverCommunication.leaderApproval(messageBuilder.updateIdentityLeader(serverState.getServerInfo().getServerId(), requestIdentity));

                        if (approved) {
                            userInfo = new UserInfo();
                            userInfo.setIdentity(requestIdentity);
                            userInfo.setCurrentChatRoom(mainHall);
                            userInfo.setManagingThread(this);
                            userInfo.setSocket(clientSocket);

                            this.setUserInfo(userInfo);

                            serverState.getConnectedClients().put(requestIdentity, userInfo);
                            serverState.getLocalChatRooms().get(mainHall).addMember(requestIdentity);

                            logger.info("Client connected: " + requestIdentity);

                            //{"type" : "newidentity", "approved" : "true"}
                            this.write(messageBuilder.newIdentityResp("true"));

                            //{"type" : "roomchange", "identity" : "Adel", "former" : "", "roomid" : "MainHall-s1"}
                            clientCommunication.broadcastMessageToRoom(messageBuilder.roomChange("", mainHall, userInfo.getIdentity()), mainHall);
                        } else {
                            this.write(messageBuilder.newIdentityResp("false"));
                        }

                    }

                }

                else if (type.equalsIgnoreCase("createroom")) {

                    String requestRoomId = (String) jsonMessage.get("roomid");
                    boolean isRoomExisted = serverState.isRoomExistedGlobally(requestRoomId);
                    boolean isRoomIdValid = Utilities.isIdValid(requestRoomId);

                    if (isRoomExisted || !isRoomIdValid || userInfo.isRoomOwner()) {
                        // {"type" : "newidentity", "approved" : "false"}
                        this.write(messageBuilder.createRoomResp(requestRoomId, "false"));
                    } else {

                        boolean approved = serverCommunication.leaderApproval(messageBuilder.updateRoomLeader(serverState.getServerInfo().getServerId(), requestRoomId));

                        if (approved) {

                            LocalChatRoom newRoom = new LocalChatRoom();
                            newRoom.setChatRoomId(requestRoomId);
                            newRoom.setOwner(userInfo.getIdentity());
                            newRoom.addMember(userInfo.getIdentity());
                            serverState.getLocalChatRooms().put(requestRoomId, newRoom);

                            String former = userInfo.getCurrentChatRoom();
                            serverState.getLocalChatRooms().get(former).removeMember(userInfo.getIdentity());

                            userInfo.setCurrentChatRoom(requestRoomId);
                            userInfo.setRoomOwner(true);

                            this.write(messageBuilder.createRoomResp(requestRoomId, "true"));
                            clientCommunication.broadcastMessageToRoom(messageBuilder.roomChange(former, userInfo.getCurrentChatRoom(), userInfo.getIdentity()), former);

                        } else {
                            this.write(messageBuilder.createRoomResp(requestRoomId, "false"));
                        }

                    }

                }

                else if ( type.equalsIgnoreCase("joinroom")){
                    // {"type" : "join", "roomid" : "jokes"}
                    String joiningRoomId = (String) jsonMessage.get("roomid");
                    boolean roomExistedGlobally = serverState.isRoomExistedGlobally(joiningRoomId);
                    boolean isTheSameRoom = userInfo.getCurrentChatRoom().equalsIgnoreCase(joiningRoomId);
                    if (userInfo.isRoomOwner() || !roomExistedGlobally || isTheSameRoom) {
                        this.write(messageBuilder.roomChange(joiningRoomId, joiningRoomId, userInfo.getIdentity()));
                    } else {

                        boolean roomExistedLocally = serverState.isRoomExistedLocally(joiningRoomId);
                        boolean roomExistedRemotely = serverState.isRoomExistedRemotely(joiningRoomId);

                        String former = userInfo.getCurrentChatRoom();

                        // If room is in the same server
                        if (roomExistedLocally) {
                            userInfo.setCurrentChatRoom(joiningRoomId);

                            serverState.getLocalChatRooms().get(joiningRoomId).addMember(userInfo.getIdentity());

                            clientCommunication.broadcastMessageToRoom(messageBuilder.roomChange(former, joiningRoomId, userInfo.getIdentity()), former, userInfo.getIdentity());
                            clientCommunication.broadcastMessageToRoom(messageBuilder.roomChange(former, joiningRoomId, userInfo.getIdentity()), joiningRoomId, userInfo.getIdentity());
                            this.write(messageBuilder.roomChange(former, joiningRoomId, userInfo.getIdentity()));
                        }

                        // If the chat room is managed by a different server
                        if (roomExistedRemotely) {
                            RemoteChatRoom remoteChatRoom = serverState.getRemoteChatRooms().get(joiningRoomId);
                            ServerInfo server = serverState.getServerInfoById(remoteChatRoom.getManagingServer());


                            this.write(messageBuilder.route(joiningRoomId, server.getAddress(), server.getClientPort()));

                            this.setRouted(true);

                            clientCommunication.broadcastMessageToRoom(messageBuilder.roomChange(former, joiningRoomId, userInfo.getIdentity()), former);

                            logger.info(userInfo.getIdentity() + " has routed to server " + server.getServerId());
                        }

                        // Either case, remove user from former room on this server memory
                        serverState.getLocalChatRooms().get(former).removeMember(userInfo.getIdentity());
                    }

                }

                else if ( type.equalsIgnoreCase("movejoin")) {

                    // {"type" : "movejoin", "former" : "MainHall-s1", "roomid" : "jokes", "identity" : "Maria"}
                    String joiningRoomId = (String) jsonMessage.get("roomid");
                    String former = (String) jsonMessage.get("former");
                    String identity = (String) jsonMessage.get("identity");
                    boolean roomExistedLocally = serverState.isRoomExistedLocally(joiningRoomId);

                    userInfo = new UserInfo();
                    userInfo.setIdentity(identity);
                    userInfo.setManagingThread(this);
                    userInfo.setSocket(clientSocket);

                    this.setUserInfo(userInfo);

                    String roomId;
                    String mainHall = "MainHall-" + serverState.getServerInfo().getServerId();
                    if (roomExistedLocally) {
                        roomId = joiningRoomId;
                    } else {
                        roomId = mainHall;
                    }
                    userInfo.setCurrentChatRoom(roomId);
                    serverState.getConnectedClients().put(identity, userInfo);
                    serverState.getLocalChatRooms().get(roomId).addMember(identity);

                    logger.info("Client connected: " + identity);

                    write(messageBuilder.serverChange("true", serverState.getServerInfo().getServerId()));
                    clientCommunication.broadcastMessageToRoom(messageBuilder.roomChange(former, roomId, userInfo.getIdentity()), roomId);
                }

                else if ( type.equalsIgnoreCase("deleteroom")) {
                    // {"type" : "deleteroom", "roomid" : "jokes"}
                    String deleteRoomId = (String) jsonMessage.get("roomid");
                    boolean roomExistedLocally = serverState.isRoomExistedLocally(deleteRoomId);
                    if (roomExistedLocally) {
                        LocalChatRoom deletingRoom = serverState.getLocalChatRooms().get(deleteRoomId);
                        if (deletingRoom.getOwner().equalsIgnoreCase(userInfo.getIdentity())) {

                            serverCommunication.commPeerOneWay(serverState.getCoordinator(), messageBuilder.deleteRoomLeader(deleteRoomId));

                            userInfo.setRoomOwner(false);
                            String mainHall = "MainHall-" + serverState.getServerInfo().getServerId();
                            userInfo.setCurrentChatRoom(mainHall);

                            serverState.getLocalChatRooms().get(mainHall).getMembers().addAll(deletingRoom.getMembers());
                            for (String member : deletingRoom.getMembers()) {
                                UserInfo client = serverState.getConnectedClients().get(member);
                                client.setCurrentChatRoom(mainHall);
                                String message = messageBuilder.roomChange(deleteRoomId, mainHall, client.getIdentity());
                                clientCommunication.broadcastMessageToRoom(message, deletingRoom.getChatRoomId());
                                clientCommunication.broadcastMessageToRoom(message, mainHall);
                            }

                            serverState.getLocalChatRooms().remove(deletingRoom.getChatRoomId());
                            this.write(messageBuilder.deleteRoom(deleteRoomId, "true"));
                        } else {
                            this.write(messageBuilder.deleteRoom(deleteRoomId, "false"));
                        }
                    } else {
                        this.write(messageBuilder.deleteRoom(deleteRoomId, "false"));
                    }

                }

                else if (type.equalsIgnoreCase("quit")){
                    String mainHall = "MainHall-" + serverState.getServerInfo().getServerId();
                    String former = userInfo.getCurrentChatRoom();


                    serverCommunication.commPeerOneWay(serverState.getCoordinator(), messageBuilder.deleteClientLeader(userInfo.getIdentity()));
                    // remove user from room
                    serverState.getLocalChatRooms().get(former).removeMember(userInfo.getIdentity());

                    // follow delete room protocol if owner
                    if (userInfo.isRoomOwner()) {

                        LocalChatRoom deletingRoom = serverState.getLocalChatRooms().get(former);
                        serverCommunication.commPeerOneWay(serverState.getCoordinator(), messageBuilder.deleteRoomLeader(deletingRoom.getChatRoomId()));
                        serverState.getLocalChatRooms().get(mainHall).getMembers().addAll(deletingRoom.getMembers());
                        for (String member : deletingRoom.getMembers()) {
                            UserInfo client = serverState.getConnectedClients().get(member);
                            client.setCurrentChatRoom(mainHall);
                            String message = messageBuilder.roomChange(deletingRoom.getChatRoomId(), mainHall, client.getIdentity());
                            clientCommunication.broadcastMessageToRoom(message, deletingRoom.getChatRoomId());
                            clientCommunication.broadcastMessageToRoom(message, mainHall);
                        }

                        // delete the room
                        serverState.getLocalChatRooms().remove(deletingRoom.getChatRoomId());
                    }

                    // remove user
                    serverState.getConnectedClients().remove(userInfo.getIdentity());

                    // update about quitting user
                    if (userInfo.isRoomOwner()) {
                        clientCommunication.broadcastMessageToRoom(messageBuilder.roomChange(former, "", userInfo.getIdentity()), mainHall, userInfo.getIdentity());
                    } else {
                        clientCommunication.broadcastMessageToRoom(messageBuilder.roomChange(former, "", userInfo.getIdentity()), former, userInfo.getIdentity());
                    }

                    write(messageBuilder.roomChange(former, "", userInfo.getIdentity()));
                    break;
                }

                else if (type.equalsIgnoreCase("who")) {

                    this.write(messageBuilder.who(userInfo.getCurrentChatRoom()));
                }

                else if (jsonMessage == null) {

                    if (userInfo != null) {
                        String mainHall = "MainHall-" + serverState.getServerInfo().getServerId();
                        String former = userInfo.getCurrentChatRoom();


                        serverCommunication.commPeerOneWay(serverState.getCoordinator(), messageBuilder.deleteClientLeader(userInfo.getIdentity()));
                        // remove user from room
                        serverState.getLocalChatRooms().get(former).removeMember(userInfo.getIdentity());

                        // follow delete room protocol if owner
                        if (userInfo.isRoomOwner()) {

                            LocalChatRoom deletingRoom = serverState.getLocalChatRooms().get(former);
                            serverCommunication.commPeerOneWay(serverState.getCoordinator(), messageBuilder.deleteRoomLeader(deletingRoom.getChatRoomId()));
                            serverState.getLocalChatRooms().get(mainHall).getMembers().addAll(deletingRoom.getMembers());
                            for (String member : deletingRoom.getMembers()) {
                                UserInfo client = serverState.getConnectedClients().get(member);
                                client.setCurrentChatRoom(mainHall);
                                String message = messageBuilder.roomChange(deletingRoom.getChatRoomId(), mainHall, client.getIdentity());
                                clientCommunication.broadcastMessageToRoom(message, deletingRoom.getChatRoomId());
                                clientCommunication.broadcastMessageToRoom(message, mainHall);
                            }

                            // delete the room
                            serverState.getLocalChatRooms().remove(deletingRoom.getChatRoomId());
                        }

                        // remove user
                        serverState.getConnectedClients().remove(userInfo.getIdentity());
                        if (!this.isRouted()) {
                            String former1 = userInfo.getCurrentChatRoom();
                            clientCommunication.broadcastMessageToRoom(messageBuilder.roomChange(former1, "", userInfo.getIdentity()), former, userInfo.getIdentity());
                        }
                    }
                    break;
                }



            }

            writer.close();
            reader.close();
            clientSocket.close();

            if (userInfo != null) {
                logger.info("Client disconnected: " + userInfo.getIdentity());
            }

        } catch (Exception e) {
            logger.debug(e.getMessage());
        } finally {

            if (!clientSocket.isClosed()) {
                try {
                    writer.close();
                    reader.close();
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void write(String msg) {
        try {
            writer.write(msg + "\n");
            writer.flush();

            logger.trace("Message flush");

        } catch (IOException e) {
            logger.trace(e.getMessage());
        }
    }


    public Socket getClientSocket() {
        return clientSocket;
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(UserInfo userInfo) {
        this.userInfo = userInfo;
    }

    public boolean isRouted() {
        return routed;
    }

    public void setRouted(boolean routed) {
        this.routed = routed;
    }


    private static final Logger logger = LogManager.getLogger(ClientConnection.class);
}
