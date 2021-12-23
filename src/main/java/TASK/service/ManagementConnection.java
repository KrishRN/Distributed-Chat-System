package TASK.service;

import TASK.consensus.BullyElection;
import TASK.model.RemoteChatRoom;
import TASK.model.RemoteUserInfo;
import TASK.server.ServerInfo;
import TASK.server.ServerState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ManagementConnection implements Runnable {

    private static final Logger logger = LogManager.getLogger(ManagementConnection.class);
    private BufferedReader reader;
    private BufferedWriter writer;
    private JSONParser parser;
    ServerState serverState = ServerState.getInstance();
    private JSONBuilder messageBuilder;
    private ServerCommunication serverCommunication;

    private Socket clientSocket;

    public ManagementConnection(Socket clientSocket) {
        try {
            this.clientSocket = clientSocket;
            this.reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));;
            this.writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"));
            this.parser = new JSONParser();
            this.messageBuilder = JSONBuilder.getInstance();
            this.serverCommunication = new ServerCommunication();
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
                logger.debug("[S2S]Receiving: " + msg);


                String type = (String) jsonMessage.get("type");

                if (type.equalsIgnoreCase("startelection")) {

                    String potentialCandidateId = (String) jsonMessage.get("serverid");
                    logger.debug("Received election msg from : " + potentialCandidateId);
                    String myServerId = serverState.getServerInfo().getServerId();

                    if (Integer.parseInt(myServerId) < Integer.parseInt(potentialCandidateId)) {
                        // tell the election requester that I have a higher priority than him
                        String potentialCandidateAddress = (String) jsonMessage.get("address");
                        Integer potentialCandidatePort = Integer.parseInt((String) jsonMessage.get("port"));
                        Integer potentialCandidateManagementPort =
                                Integer.parseInt((String) jsonMessage.get("managementport"));
                        ServerInfo potentialCandidate =
                                new ServerInfo(potentialCandidateId, potentialCandidateAddress, potentialCandidatePort,
                                        potentialCandidateManagementPort);

                        new BullyElection()
                                .replyAnswerForElectionMessage(potentialCandidate, serverState.getServerInfo());

                        // start a new election among the servers that have a higher priority

                        new BullyElection()
                                .startElection(serverState.getServerInfo(), serverState.getCandidateServerInfoList());

                        new BullyElection()
                                .startWaitingForAnswerMessage(serverState.getServerInfo(), serverState.getElectionAnswerTimeout());

                    }

                }

                else if (type.equalsIgnoreCase("answerelection")){
                    // received an answer message from a higher priority server
                    // start waiting for the coordinator message
                    logger.debug("Received answer from : " + jsonMessage.get("serverid"));

                    // since the answer message timeout is no longer needed, stop that timeout first
                    new BullyElection().stopWaitingForAnswerMessage(serverState.getServerInfo());

                    // start waiting for the coordinator message
                    new BullyElection().startWaitingForCoordinatorMessage(
                            serverState.getServerInfo(),
                            serverState.getElectionCoordinatorTimeout());

                }
                else if (type.equalsIgnoreCase("coordinator")) {
                    // stop its election
                    logger.debug("Received coordinator from : " + jsonMessage.get("serverid"));

                    new BullyElection().stopElection(serverState.getServerInfo());

                    // accept the new coordinator
                    String newCoordinatorId = (String) jsonMessage.get("serverid");
                    String newCoordinatorAddress = (String) jsonMessage.get("address");
                    Integer newCoordinatorPort = Integer.parseInt((String) jsonMessage.get("port"));
                    Integer newCoordinatorManagementPort =
                            Integer.parseInt((String) jsonMessage.get("managementport"));
                    ServerInfo newCoordinator = new ServerInfo(newCoordinatorId, newCoordinatorAddress, newCoordinatorPort,
                            newCoordinatorManagementPort);

                    new BullyElection().acceptNewCoordinator(newCoordinator);
                    logger.debug("Accepted new Coordinator : " + newCoordinatorId);

                }

                else if(type.equalsIgnoreCase("serverup")){

                    String serverId = (String) jsonMessage.get("serverid");
                    String address = (String) jsonMessage.get("address");
                    Long port = (Long) jsonMessage.get("port");
                    Long managementPort = (Long) jsonMessage.get("managementport");

                    ServerInfo serverInfo = new ServerInfo();
                    serverInfo.setAddress(address);
                    serverInfo.setServerId(serverId);
                    serverInfo.setPort(Math.toIntExact(port));
                    serverInfo.setManagementPort(Math.toIntExact(managementPort));

                    serverState.addServer(serverInfo);

                    break;
                }

                else if(type.equalsIgnoreCase("notifyserverdown")) {

                    String serverId = (String) jsonMessage.get("serverid");

                    logger.debug("Server down notification received. Removing server: " + serverId);

                    serverState.removeServer(serverId);
                    serverState.removeRemoteChatRoomsByServerId(serverId);
                    serverState.removeRemoteClientsByServerId(serverId);

                    break;

                }
                else if (type.equalsIgnoreCase("updateIdentityLeader")) {
                    //only leader can process this update msg which is send when a client trying to make connection with a follower
//                    if (serverState.getServerInfo().getServerId().equalsIgnoreCase(serverState.getCoordinator().getServerId())){
                        String requestUserId = (String) jsonMessage.get("identity");
                        String serverId = (String) jsonMessage.get("serverid");
                        boolean isUserExisted = serverState.isUserExisted(requestUserId);

                        if (isUserExisted) {
                            this.write(messageBuilder.updateIdentityConfirm("false"));
                        } else {
                            serverState.getRemoteClients().put(requestUserId, new RemoteUserInfo(requestUserId, serverId));
                            this.write(messageBuilder.updateIdentityConfirm("true"));
                            serverCommunication.relayPeers(messageBuilder.updateIdentityServer(serverId, requestUserId));
                        }
                        break;

                }

                else if (type.equalsIgnoreCase("updateIdentityServer")) {

                    String requestUserId = (String) jsonMessage.get("identity");
                    String serverId = (String) jsonMessage.get("serverid");

                    serverState.getRemoteClients().put(requestUserId, new RemoteUserInfo(requestUserId, serverId));
                    break;
                }

                else if (type.equalsIgnoreCase("updateRoomLeader")) {
                    //only leader can process this update msg which is send when a client trying to create a new room in a follower node
                    String requestRoomId = (String) jsonMessage.get("roomid");
                    String serverId = (String) jsonMessage.get("serverid");
                    boolean isRoomExisted = serverState.isRoomExistedGlobally(requestRoomId);

                    if (isRoomExisted) {
                        this.write(messageBuilder.updateRoomConfirm("false"));
                    } else {
                        RemoteChatRoom RC = new RemoteChatRoom();
                        RC.setChatRoomId(requestRoomId);
                        RC.setManagingServer(serverId);
                        serverState.getRemoteChatRooms().put(requestRoomId, RC);
                        this.write(messageBuilder.updateRoomConfirm("true"));
                        serverCommunication.relayPeers(messageBuilder.updateRoomServer(serverId, requestRoomId));
                    }
                    break;

                }

                else if (type.equalsIgnoreCase("updateRoomServer")) {

                    String requestRoomId = (String) jsonMessage.get("roomid");
                    String serverId = (String) jsonMessage.get("serverid");

                    RemoteChatRoom RC = new RemoteChatRoom();
                    RC.setChatRoomId(requestRoomId);
                    RC.setManagingServer(serverId);

                    serverState.getRemoteChatRooms().put(requestRoomId, RC);
                    break;
                }

                else if (type.equalsIgnoreCase("deleteRoomLeader")) {

                    String deletingRoomId = (String) jsonMessage.get("roomid");
                    serverState.getRemoteChatRooms().remove(deletingRoomId);
                    serverCommunication.relayPeers(messageBuilder.deleteRoomServer(deletingRoomId));
                    break;
                }

                else if (type.equalsIgnoreCase("deleteRoomServer")) {

                    String deletingRoomId = (String) jsonMessage.get("roomid");
                    serverState.getRemoteChatRooms().remove(deletingRoomId);
                    break;
                }

                else if (type.equalsIgnoreCase("deleteClientLeader")) {

                    String deletingClientId = (String) jsonMessage.get("identity");
                    serverState.getRemoteClients().remove(deletingClientId);
                    serverCommunication.relayPeers(messageBuilder.deleteClientServer(deletingClientId));
                    break;
                }

                else if (type.equalsIgnoreCase("deleteClientServer")) {

                    String deletingClientId = (String) jsonMessage.get("identity");
                    serverState.getRemoteClients().remove(deletingClientId);
                    break;
                }






            }

            clientSocket.close();
            writer.close();
            reader.close();

        } catch (IOException | ParseException e) {
            logger.trace(e.getMessage());
        }

    }

    private void write(String msg) {
        try {
            writer.write(msg + "\n");
            writer.flush();
        } catch (IOException e) {
            logger.trace(e.getMessage());
        }
    }

    public Socket getClientSocket() {
        return clientSocket;
    }
}
