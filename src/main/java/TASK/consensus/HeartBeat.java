package TASK.consensus;

import TASK.server.ServerInfo;
import TASK.server.ServerState;
import TASK.service.JSONBuilder;
import TASK.service.ServerCommunication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class HeartBeat implements Job {
    private static final Logger logger = LogManager.getLogger(HeartBeat.class);
    private ServerState serverState = ServerState.getInstance();
    private JSONBuilder messageBuilder = JSONBuilder.getInstance();
    private ServerCommunication serverCommunication = new ServerCommunication();

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {

        if (null != serverState.getCoordinator()) {
            // let it put in trace for now, bec take debug as default dev mode
            logger.trace("Current coordinator is : " + serverState.getCoordinator().getServerId());
        }

        for (ServerInfo serverInfo : serverState.getServerInfoList()) {
            String serverId = serverInfo.getServerId();
            String myServerId = serverState.getServerInfo().getServerId();
            if (serverId.equalsIgnoreCase(myServerId)) {
                continue;
            }

            boolean online = serverCommunication.isOnline(serverInfo);
            if (!online) {
                Integer count = serverState.getAliveMap().get(serverId);
                if (count == null) {
                    serverState.getAliveMap().put(serverId, 1);
                } else {
                    serverState.getAliveMap().put(serverId, count + 1);
                }

                JobDataMap dataMap = context.getJobDetail().getJobDataMap();
                String aliveErrorFactor = dataMap.get("aliveErrorFactor").toString();

                count = serverState.getAliveMap().get(serverId);

                if (count > Integer.parseInt(aliveErrorFactor)) {
                    // if the offline server is the coordinator, start the election process
                    if (null != serverState.getCoordinator() && serverInfo.getServerId().equalsIgnoreCase(serverState
                            .getCoordinator().getServerId())) {
                        // send the start election message to every server with a higher priority
                        new BullyElection().startElection(serverState.getServerInfo(),
                                    serverState.getCandidateServerInfoList());

                        new BullyElection().startWaitingForAnswerMessage(
                                    serverState.getServerInfo(), serverState.getElectionAnswerTimeout());

                    }
                    serverCommunication.relayPeers(messageBuilder.notifyServerDownMessage(serverId));
                    logger.debug("Notify server " + serverId + " down. Removing...");

                    serverState.removeServer(serverId);
                    serverState.removeRemoteChatRoomsByServerId(serverId);
                    serverState.removeRemoteClientsByServerId(serverId);
                }
            }
        }
    }


}
