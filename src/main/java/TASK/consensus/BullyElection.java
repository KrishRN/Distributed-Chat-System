package TASK.consensus;

import TASK.server.ServerInfo;
import TASK.server.ServerState;
import TASK.service.JSONBuilder;
import TASK.service.Quartz;
import TASK.service.ServerCommunication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.*;
import org.quartz.Scheduler;

import java.util.List;

public class BullyElection {

    private static final Logger logger = LogManager.getLogger(BullyElection.class);
    protected JSONBuilder jsonBuilder;
    protected ServerCommunication serverCommunication;
    protected ServerState serverState;
    protected Scheduler scheduler;

    public BullyElection() {
        this.jsonBuilder = JSONBuilder.getInstance();
        this.serverCommunication = new ServerCommunication();
        this.serverState = ServerState.getInstance();
        this.scheduler = Quartz.getInstance().getScheduler();
    }

    public void startElection(ServerInfo proposingCoordinator, List<ServerInfo> candidatesList) {
        logger.debug("Starting election...");
        String proposingCoordinatorServerId = proposingCoordinator.getServerId();
        String proposingCoordinatorAddress = proposingCoordinator.getAddress();
        Long proposingCoordinatorPort = Long.valueOf(proposingCoordinator.getClientPort());
        Long proposingCoordinatorManagementPort = Long.valueOf(proposingCoordinator.getServerPort());
        String startElectionMessage = jsonBuilder
                .startElectionMessage(proposingCoordinatorServerId, proposingCoordinatorAddress,
                        proposingCoordinatorPort, proposingCoordinatorManagementPort);
        serverCommunication.relaySelectedPeers(candidatesList, startElectionMessage);
    }

    public void startWaitingTimer(String groupId, Long timeout, JobDetail jobDetail) {
        try {

            logger.debug(String.format("Starting the waiting job [%s] : %s",
                    scheduler.getSchedulerName(), jobDetail.getKey()));

            if (scheduler.checkExists(jobDetail.getKey())) {

                logger.debug(String.format("Job get trigger again [%s]", jobDetail.getKey().getName()));
                scheduler.triggerJob(jobDetail.getKey());

            } else {
                SimpleTrigger simpleTrigger =
                        (SimpleTrigger) TriggerBuilder.newTrigger()
                                .withIdentity("ELECTION_TRIGGER", groupId)
                                .startAt(DateBuilder.futureDate(Math.toIntExact(timeout), DateBuilder.IntervalUnit.SECOND))
                                .build();

                scheduler.scheduleJob(jobDetail, simpleTrigger);
            }

        } catch (ObjectAlreadyExistsException oe) {

            // FIX this is fine, bec, since trigger is there, we can safely trigger the job, again!
            logger.debug(oe.getMessage());

            try {

                logger.debug(String.format("Job get trigger again [%s]", jobDetail.getKey().getName()));
                scheduler.triggerJob(jobDetail.getKey());

                //System.err.println(Arrays.toString(scheduler.getTriggerKeys(GroupMatcher.anyGroup()).toArray()));
                // [DEFAULT.MT_e8f718prrj3ol, group1.GOSSIPJOBTRIGGER, group1.CONSENSUSJOBTRIGGER, group_fast_bully.ELECTION_TRIGGER]

            } catch (SchedulerException e) {
                e.printStackTrace();
            }

        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }

    public void startWaitingForCoordinatorMessage(ServerInfo proposingCoordinator, Long timeout) {
        JobDetail coordinatorMsgTimeoutJob =
                JobBuilder.newJob(ElectionCoordinatorMessageTimeoutFinalizer.class).withIdentity
                        ("coordinator_msg_timeout_job", "group_" + proposingCoordinator.getServerId()).build();
        startWaitingTimer("group_" + proposingCoordinator.getServerId(), timeout, coordinatorMsgTimeoutJob);
    }

    public void startWaitingForAnswerMessage(ServerInfo proposingCoordinator, Long timeout) {
        JobDetail answerMsgTimeoutJob =
                JobBuilder.newJob(ElectionAnswerMessageTimeoutFinalizer.class).withIdentity
                        ("answer_msg_timeout_job", "group_" + proposingCoordinator.getServerId()).build();
        startWaitingTimer("group_" + proposingCoordinator.getServerId(), timeout, answerMsgTimeoutJob);
    }

    public void replyAnswerForElectionMessage(ServerInfo requestingCandidate, ServerInfo me) {
        logger.debug("Replying answer for the election start message from : " + requestingCandidate.getServerId());
        String electionAnswerMessage = jsonBuilder
                .electionAnswerMessage(me.getServerId(), me.getAddress(), me.getClientPort(), me.getServerPort());
        serverCommunication.commPeerOneWay(requestingCandidate, electionAnswerMessage);
    }

    public void setupNewCoordinator(ServerInfo newCoordinator, List<ServerInfo> subordinateServerInfoList) {
        logger.debug("Informing subordinates about the new coordinator...");
        // inform subordinates about the new coordinator
        String newCoordinatorServerId = newCoordinator.getServerId();
        String newCoordinatorAddress = newCoordinator.getAddress();
        Integer newCoordinatorServerPort = newCoordinator.getClientPort();
        Integer newCoordinatorServerManagementPort = newCoordinator.getServerPort();
        String setCoordinatorMessage = jsonBuilder
                .setCoordinatorMessage(newCoordinatorServerId, newCoordinatorAddress, newCoordinatorServerPort,
                        newCoordinatorServerManagementPort);
        serverCommunication.relaySelectedPeers(subordinateServerInfoList, setCoordinatorMessage);

        // accept the new coordinator
        acceptNewCoordinator(newCoordinator);
    }

    public void acceptNewCoordinator(ServerInfo newCoordinator) {
        serverState.setCoordinator(newCoordinator);
        serverState.setOngoingElection(false);
        logger.debug("Accepting new coordinator : " + newCoordinator.getServerId());
    }

    public void stopWaitingTimer(JobKey jobKey) {
        try {
            if (scheduler.checkExists(jobKey)) {
                scheduler.interrupt(jobKey);
                //scheduler.deleteJob(jobKey);
                logger.debug(String.format("Job [%s] get interrupted from [%s]",
                        jobKey, scheduler.getSchedulerName()));
            }
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }

    public void stopWaitingForCoordinatorMessage(ServerInfo stoppingServer) {
        JobKey coordinatorMsgTimeoutJobKey =
                new JobKey("coordinator_msg_timeout_job", "group_" + stoppingServer.getServerId());
        stopWaitingTimer(coordinatorMsgTimeoutJobKey);
    }

    public void stopWaitingForAnswerMessage(ServerInfo stoppingServer) {
        JobKey answerMsgTimeoutJobKey =
                new JobKey("answer_msg_timeout_job", "group_" + stoppingServer.getServerId());
        stopWaitingTimer(answerMsgTimeoutJobKey);
    }

    public void stopElection(ServerInfo stoppingServer) {
        logger.debug("Stopping election...");
        stopWaitingForAnswerMessage(stoppingServer);
        stopWaitingForCoordinatorMessage(stoppingServer);
    }


}
