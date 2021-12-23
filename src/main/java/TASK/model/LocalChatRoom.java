package TASK.model;

import java.util.ArrayList;
import java.util.List;

public class LocalChatRoom extends ChatRoom {
    private String owner;
    private List<String> members = new ArrayList<>();

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public synchronized void addMember(String client) {
        members.add(client);
    }

    public synchronized void removeMember(String client) {
        members.remove(client);
    }

    public synchronized List<String> getMembers() {
        return members;
    }
}
