package TASK.service;

import TASK.server.ServerState;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import TASK.server.ServerInfo;
import TASK.model.ChatRoom;
import TASK.model.LocalChatRoom;

import java.util.stream.Collectors;

public class JSONBuilder {

    private static JSONBuilder instance = null;

    private JSONBuilder() {
    }

    public static synchronized JSONBuilder getInstance() {
        if (instance == null) instance = new JSONBuilder();
        return instance;
    }

    public String serverChange(String approved, String serverId) {
        // {"type" : "serverchange", "approved" : "true", "serverid" : "s2"}
        JSONObject jj = new JSONObject();
        jj.put("type", "serverchange");
        jj.put("approved", approved);
        jj.put("serverid", serverId);
        return jj.toJSONString();
    }

    public String route(String joiningRoomId, String host, Integer port) {
        // {"type" : "route", "roomid" : "jokes", "host" : "122.134.2.4", "port" : "4445"}
        JSONObject jj = new JSONObject();
        jj.put("type", "route");
        jj.put("roomid", joiningRoomId);
        jj.put("host", host);
        jj.put("port", port.toString());
        return jj.toJSONString();
    }


    public String message(String identity, String content) {
        // {"type" : "message", "identity" : "Adel", "content" : "Hi there!"}
        JSONObject jj = new JSONObject();
        jj.put("type", "message");
        jj.put("identity", identity);
        jj.put("content", content);
        return jj.toJSONString();
    }

    public String deleteRoom(String roomId, String approved) {
        // {"type" : "deleteroom", "roomid" : "jokes", "approved" : "true"}
        JSONObject jj = new JSONObject();
        jj.put("type", "deleteroom");
        jj.put("roomid", roomId);
        jj.put("approved", approved);
        return jj.toJSONString();
    }


    public String who(String room) {
        JSONObject jj = new JSONObject();
        //{ "type" : "roomcontents", "roomid" : "jokes", "identities" : ["Adel","Chenhao","Maria"], "owner" : "Adel" }
        jj.put("type", "roomcontents");
        jj.put("roomid", room);
        LocalChatRoom localChatRoom = ServerState.getInstance().getLocalChatRooms().get(room);
        JSONArray ja = new JSONArray();
        ja.addAll(localChatRoom.getMembers());
        jj.put("identities", ja);
        jj.put("owner", localChatRoom.getOwner());
        return jj.toJSONString();
    }

    public String listRooms() {
        //{ "type" : "roomlist", "rooms" : ["MainHall-s1", "MainHall-s2", "jokes"] }
        JSONObject jj = new JSONObject();
        jj.put("type", "roomlist");

        JSONArray ja = new JSONArray();

        ja.addAll(ServerState.getInstance().getRemoteChatRooms().values().stream()
                .map(ChatRoom::getChatRoomId)
                .collect(Collectors.toList()));

        jj.put("rooms", ja);

        return jj.toJSONString();
    }


    public String newIdentityResp(String approve) {
        JSONObject jj = new JSONObject();
        jj.put("type", "newidentity");
        jj.put("approved", approve);
        return jj.toJSONString();
    }

    public String roomChange(String former, String roomId, String identity) {
        // {"type" : "roomchange", "identity" : "Maria", "former" : "jokes", "roomid" : "jokes"}
        JSONObject jj = new JSONObject();
        jj.put("type", "roomchange");
        jj.put("identity", identity);
        jj.put("former", former);
        jj.put("roomid", roomId);
        return jj.toJSONString();
    }

    public String createRoomResp(String roomId, String approved) {
        //{"type" : "createroom", "roomid" : "jokes", "approved" : "false"}
        JSONObject jj = new JSONObject();
        jj.put("type", "createroom");
        jj.put("roomid", roomId);
        jj.put("approved", approved);
        return jj.toJSONString();
    }


    public String notifyServerDownMessage(String serverId) {
        // {"type":"notifyserverdown", "serverid":"s2"}
        JSONObject jj = new JSONObject();
        jj.put("type", "notifyserverdown");
        jj.put("serverid", serverId);
        return jj.toJSONString();
    }
    private final ServerInfo serverInfo = ServerState.getInstance().getServerInfo();

    public String serverUpMessage() {
        JSONObject jj = new JSONObject();
        jj.put("type", "serverup");
        jj.put("serverid", serverInfo.getServerId());
        jj.put("address", serverInfo.getAddress());
        jj.put("port", serverInfo.getClientPort());
        jj.put("managementport", serverInfo.getServerPort());
        return jj.toJSONString();
    }

    public String startElectionMessage(String serverId, String serverAddress, Long serverPort, Long
            serverManagementPort) {
        JSONObject jj = new JSONObject();
        jj.put("type", "startelection");
        jj.put("serverid", serverId);
        jj.put("address", serverAddress);
        jj.put("port", String.valueOf(serverPort));
        jj.put("managementport", String.valueOf(serverManagementPort));
        return jj.toJSONString();
    }

    public String electionAnswerMessage(String serverId, String serverAddress, Integer serverPort, Integer
            serverManagementPort) {
        JSONObject jj = new JSONObject();
        jj.put("type", "answerelection");
        jj.put("serverid", serverId);
        jj.put("address", serverAddress);
        jj.put("port", String.valueOf(serverPort));
        jj.put("managementport", String.valueOf(serverManagementPort));
        return jj.toJSONString();
    }

    public String setCoordinatorMessage(String serverId, String serverAddress, Integer serverPort, Integer
            serverManagementPort) {
        JSONObject jj = new JSONObject();
        jj.put("type", "coordinator");
        jj.put("serverid", serverId);
        jj.put("address", serverAddress);
        jj.put("port", String.valueOf(serverPort));
        jj.put("managementport", String.valueOf(serverManagementPort));
        return jj.toJSONString();
    }

    public String updateIdentityLeader(String serverId, String identity) {

        JSONObject jj = new JSONObject();
        jj.put("type", "updateIdentityLeader");
        jj.put("identity", identity);
        jj.put("serverid", serverId);
        return jj.toJSONString();
    }

    public String updateIdentityConfirm(String approve) {

        JSONObject jj = new JSONObject();
        jj.put("type", "updateIdentityConfirm");
        jj.put("approved", approve);
        return jj.toJSONString();
    }

    public String updateIdentityServer(String serverId, String identity) {

        JSONObject jj = new JSONObject();
        jj.put("type", "updateIdentityServer");
        jj.put("identity", identity);
        jj.put("serverid", serverId);
        return jj.toJSONString();
    }

    public String updateRoomLeader(String serverId, String roomid) {

        JSONObject jj = new JSONObject();
        jj.put("type", "updateRoomLeader");
        jj.put("roomid", roomid);
        jj.put("serverid", serverId);
        return jj.toJSONString();
    }

    public String updateRoomConfirm(String approve) {

        JSONObject jj = new JSONObject();
        jj.put("type", "updateRoomConfirm");
        jj.put("approved", approve);
        return jj.toJSONString();
    }

    public String updateRoomServer(String serverId, String roomid) {

        JSONObject jj = new JSONObject();
        jj.put("type", "updateRoomServer");
        jj.put("roomid", roomid);
        jj.put("serverid", serverId);
        return jj.toJSONString();
    }

    public String deleteRoomLeader(String roomid) {

        JSONObject jj = new JSONObject();
        jj.put("type", "deleteRoomLeader");
        jj.put("roomid", roomid);
        return jj.toJSONString();
    }

    public String deleteRoomServer(String roomid) {

        JSONObject jj = new JSONObject();
        jj.put("type", "deleteRoomServer");
        jj.put("roomid", roomid);
        return jj.toJSONString();
    }

    public String deleteClientLeader(String identity) {

        JSONObject jj = new JSONObject();
        jj.put("type", "deleteClientLeader");
        jj.put("identity", identity);
        return jj.toJSONString();
    }

    public String deleteClientServer(String identity) {

        JSONObject jj = new JSONObject();
        jj.put("type", "deleteClientServer");
        jj.put("identity", identity);
        return jj.toJSONString();
    }



}

