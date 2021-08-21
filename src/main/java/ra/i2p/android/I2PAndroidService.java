package ra.i2p.android;

import ra.common.Client;
import ra.common.Envelope;
import ra.common.messaging.MessageProducer;
import ra.common.network.*;
import ra.common.route.Route;
import ra.common.service.ServiceStatus;
import ra.common.service.ServiceStatusObserver;
import ra.common.Config;
import ra.common.Wait;
import ra.common.tasks.TaskRunner;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Provides an API for I2P Router as a Service.
 */
public final class I2PAndroidService extends NetworkService {

    private static final Logger LOG = Logger.getLogger(I2PAndroidService.class.getName());

    public static final String OPERATION_SEND = "SEND";
    public static final String OPERATION_CHECK_ROUTER_STATUS = "CHECK_ROUTER_STATUS";
    public static final String OPERATION_LOCAL_PEER_COUNTRY = "LOCAL_PEER_COUNTRY";
    public static final String OPERATION_REMOTE_PEER_COUNTRY = "REMOTE_PEER_COUNTRY";
    public static final String OPERATION_IN_STRICT_COUNTRY = "IN_STRICT_COUNTRY";
    public static final String OPERATION_UPDATE_HIDDEN_MODE = "UPDATE_HIDDEN_MODE";
    public static final String OPERATION_UPDATE_SHARE_PERCENTAGE = "UPDATE_SHARE_PERCENTAGE";
    public static final String OPERATION_UPDATE_GEOIP_ENABLEMENT = "UPDATE_GEOIP_ENABLEMENT";
    public static final String OPERATION_ACTIVE_PEERS_COUNT = "ACTIVE_PEERS_COUNT";

    /**
     * 1 = ElGamal-2048 / DSA-1024
     * 2 = ECDH-256 / ECDSA-256
     * 3 = ECDH-521 / ECDSA-521
     * 4 = NTRUEncrypt-1087 / GMSS-512
     */
    protected static int ElGamal2048DSA1024 = 1;
    protected static int ECDH256ECDSA256 = 2;
    protected static int ECDH521EDCSA521 = 3;
    protected static int NTRUEncrypt1087GMSS512 = 4;

    private File i2pDir;

    private Thread taskRunnerThread;
    private Long startTimeBlockedMs = 0L;
    private static final Long BLOCK_TIME_UNTIL_RESTART = 3 * 60 * 1000L; // 4 minutes
    private Integer restartAttempts = 0;
    private static final Integer RESTART_ATTEMPTS_UNTIL_HARD_RESTART = 3;
    private boolean embedded = true;
    private boolean isTest = false;
    private TaskRunner taskRunner;
    private Map<String, I2PSession> sessions = new HashMap<>();

    final Map<String,Long> inflightTimers = new HashMap<>();

    public I2PAndroidService() {
        super(Network.I2P);
    }

    public I2PAndroidService(MessageProducer messageProducer, ServiceStatusObserver observer) {
        super(Network.I2P, messageProducer, observer);
    }

    @Override
    public void handleDocument(Envelope e) {
        super.handleDocument(e);
        Route r = e.getRoute();
        switch(r.getOperation()) {
            case OPERATION_SEND: {
                sendOut(e);
                break;
            }
            case OPERATION_CHECK_ROUTER_STATUS: {
                checkRouterStats();
                break;
            }
            case OPERATION_LOCAL_PEER_COUNTRY: {
                NetworkPeer localPeer = getNetworkState().localPeer;
                if(localPeer==null) {
                    e.addNVP("country", "NoLocalPeer");
                } else {
                    e.addNVP("country", country(localPeer));
                }
                break;
            }
            case OPERATION_REMOTE_PEER_COUNTRY: {
                NetworkPeer remotePeer = (NetworkPeer)e.getValue("remotePeer");
                if(remotePeer==null) {
                    e.addNVP("country", "NoRemotePeer");
                } else {
                    e.addNVP("country", country(remotePeer));
                }
                break;
            }
            case OPERATION_IN_STRICT_COUNTRY: {
                NetworkPeer peer = (NetworkPeer)e.getValue("peer");
                if(peer==null) {
                    e.addNVP("localPeerCountry", inStrictCountry());
                } else {
                    e.addNVP("peerCountry", inStrictCountry(peer));
                }
                break;
            }
            case OPERATION_UPDATE_HIDDEN_MODE: {
                Object hiddenModeObj = e.getValue("hiddenMode");
                if(hiddenModeObj!=null) {
                    updateHiddenMode((((String)hiddenModeObj).toLowerCase()).equals("true"));
                }
                break;
            }
            case OPERATION_UPDATE_SHARE_PERCENTAGE: {
                Object sharePerc = e.getValue("sharePercentage");
                if(sharePerc!=null) {
                    updateSharePercentage(Integer.parseInt((String)sharePerc));
                }
                break;
            }
            case OPERATION_UPDATE_GEOIP_ENABLEMENT: {
                Object sharePerc = e.getValue("enableGeoIP");
                if(sharePerc!=null) {
                    updateGeoIPEnablement((((String)sharePerc).toLowerCase()).equals("true"));
                }
                break;
            }
            case OPERATION_ACTIVE_PEERS_COUNT: {
                Integer count = activePeersCount();
                e.addNVP("activePeersCount", count);
                break;
            }
            default: {
                LOG.warning("Operation ("+r.getOperation()+") not supported. Sending to Dead Letter queue.");
                deadLetter(e);
            }
        }
    }

    private I2PSession establishSession(String address, Boolean autoConnect) {
        if(address==null) {
            address = "default";
        }
        if(sessions.get(address)==null) {
            I2PSession session = new I2PSession(this);
            session.init(config);
            session.open(null);
            if (autoConnect) {
                session.connect();
            }
            sessions.put(address, session);
        }
        return sessions.get(address);
    }

    /**
     * Sends UTF-8 content to a Destination using I2P.
     * @param envelope Envelope containing Envelope as data.
     *                 To DID must contain base64 encoded I2P destination key.
     * @return boolean was successful
     */
    public Boolean sendOut(Envelope envelope) {
        LOG.fine("Send out Envelope over I2P...");
        NetworkClientSession session = establishSession(null, true);
        return session.send(envelope);
    }

    public File getDirectory() {
        return i2pDir;
    }

    private void updateHiddenMode(boolean hiddenMode) {
        String hiddenModeStr = hiddenMode?"true":"false";
//        if(!(getNetworkState().params.get(Router.PROP_HIDDEN)).equals(hiddenModeStr)) {
//            // Hidden mode changed so change for Router and restart
//            this.getNetworkState().params.put(Router.PROP_HIDDEN, hiddenModeStr);
//            if (router.saveConfig(Router.PROP_HIDDEN, hiddenModeStr)) {
//                restart();
//            } else {
//                LOG.warning("Unable to update " + Router.PROP_HIDDEN);
//            }
//        }
    }

    private void updateSharePercentage(int sharePercentage) {
        if(!(getNetworkState().params.get("router.sharePercentage")).equals(String.valueOf(sharePercentage))) {
            // Share Percentage changed so change for Router and restart
//            this.getNetworkState().params.put(Router.PROP_HIDDEN, String.valueOf(sharePercentage));
//            if (router.saveConfig(Router.PROP_HIDDEN, String.valueOf(sharePercentage))) {
//                restart();
//            } else {
                LOG.warning("Unable to update router.sharePercentage");
//            }
        }
    }

    private void updateGeoIPEnablement(boolean enableGeoIP) {
        String enableGeoIPStr = enableGeoIP?"true":"false";
//        if(!(getNetworkState().params.get("routerconsole.geoip.enable")).equals(enableGeoIPStr)) {
//            // Hidden mode changed so change for Router and restart
//            this.getNetworkState().params.put("routerconsole.geoip.enable", enableGeoIPStr);
//            if (router.saveConfig("routerconsole.geoip.enable", enableGeoIPStr)) {
//                restart();
//            } else {
                LOG.warning("Unable to update routerconsole.geoip.enable");
//            }
//        }
    }

    public boolean start(Properties p) {
        LOG.info("Starting I2P Service...");
        updateStatus(ServiceStatus.INITIALIZING);
        LOG.info("Loading I2P properties...");
        try {
            // Load environment variables first
            config = Config.loadAll(p, "i2p-android.config");
        } catch (Exception e) {
            LOG.severe(e.getLocalizedMessage());
            return false;
        }

        // TODO: Verify Client is available

        updateStatus(ServiceStatus.RUNNING);

        return true;
    }

    @Override
    public boolean pause() {
        return false;
    }

    @Override
    public boolean unpause() {
        return false;
    }

    @Override
    public boolean restart() {

        return true;
    }

    @Override
    public boolean shutdown() {
        updateStatus(ServiceStatus.SHUTTING_DOWN);
        LOG.info("I2P router stopping...");
        // TODO: Signal to I2P Router to shutdown
        updateStatus(ServiceStatus.SHUTDOWN);
        LOG.info("I2P router stopped.");
        return true;
    }

    @Override
    public boolean gracefulShutdown() {
        updateStatus(ServiceStatus.GRACEFULLY_SHUTTING_DOWN);
        LOG.info("I2P router gracefully stopping...");
        // TODO: Signal to I2P Router to gracefully shutdown
        updateStatus(ServiceStatus.GRACEFULLY_SHUTDOWN);
        LOG.info("I2P router gracefully stopped.");
        return true;
    }

    public void reportRouterStatus() {
//        switch (i2pRouterStatus) {
//            case UNKNOWN:
//                LOG.info("Testing I2P Network...");
//                updateNetworkStatus(NetworkStatus.CONNECTING);
//                break;
//            case IPV4_DISABLED_IPV6_UNKNOWN:
//                LOG.info("IPV4 Disabled but IPV6 Testing...");
//                updateNetworkStatus(NetworkStatus.CONNECTING);
//                break;
//            case IPV4_FIREWALLED_IPV6_UNKNOWN:
//                LOG.info("IPV4 Firewalled but IPV6 Testing...");
//                updateNetworkStatus(NetworkStatus.CONNECTING);
//                break;
//            case IPV4_SNAT_IPV6_UNKNOWN:
//                LOG.info("IPV4 SNAT but IPV6 Testing...");
//                updateNetworkStatus(NetworkStatus.CONNECTING);
//                break;
//            case IPV4_UNKNOWN_IPV6_FIREWALLED:
//                LOG.info("IPV6 Firewalled but IPV4 Testing...");
//                updateNetworkStatus(NetworkStatus.CONNECTING);
//                break;
//            case OK:
//                LOG.info("Connected to I2P Network. We are able to receive unsolicited connections.");
//                restartAttempts = 0; // Reset restart attempts
//                updateNetworkStatus(NetworkStatus.CONNECTED);
//                break;
//            case IPV4_DISABLED_IPV6_OK:
//                LOG.info("IPV4 Disabled but IPV6 OK: Connected to I2P Network.");
//                restartAttempts = 0; // Reset restart attempts
//                updateNetworkStatus(NetworkStatus.CONNECTED);
//                break;
//            case IPV4_FIREWALLED_IPV6_OK:
//                LOG.info("IPV4 Firewalled but IPV6 OK: Connected to I2P Network.");
//                restartAttempts = 0; // Reset restart attempts
//                updateNetworkStatus(NetworkStatus.CONNECTED);
//                break;
//            case IPV4_SNAT_IPV6_OK:
//                LOG.info("IPV4 SNAT but IPV6 OK: Connected to I2P Network.");
//                restartAttempts = 0; // Reset restart attempts
//                updateNetworkStatus(NetworkStatus.CONNECTED);
//                break;
//            case IPV4_UNKNOWN_IPV6_OK:
//                LOG.info("IPV4 Testing but IPV6 OK: Connected to I2P Network.");
//                restartAttempts = 0; // Reset restart attempts
//                updateNetworkStatus(NetworkStatus.CONNECTED);
//                break;
//            case IPV4_OK_IPV6_FIREWALLED:
//                LOG.info("IPV6 Firewalled but IPV4 OK: Connected to I2P Network.");
//                restartAttempts = 0; // Reset restart attempts
//                updateNetworkStatus(NetworkStatus.CONNECTED);
//                break;
//            case IPV4_OK_IPV6_UNKNOWN:
//                LOG.info("IPV6 Testing but IPV4 OK: Connected to I2P Network.");
//                restartAttempts = 0; // Reset restart attempts
//                updateNetworkStatus(NetworkStatus.CONNECTED);
//                break;
//            case IPV4_DISABLED_IPV6_FIREWALLED:
//                LOG.warning("IPV4 Disabled but IPV6 Firewalled. Connected to I2P network.");
//                restartAttempts = 0; // Reset restart attempts
//                updateNetworkStatus(NetworkStatus.CONNECTED);
//                break;
//            case REJECT_UNSOLICITED:
//                LOG.info("We are able to talk to peers that we initiate communication with, but cannot receive unsolicited connections. Connected to I2P network.");
//                restartAttempts = 0; // Reset restart attempts
//                updateNetworkStatus(NetworkStatus.CONNECTED);
//                break;
//            case DISCONNECTED:
//                LOG.info("Disconnected from I2P Network.");
//                updateNetworkStatus(NetworkStatus.DISCONNECTED);
//                restart();
//                break;
//            case DIFFERENT:
//                LOG.warning("Symmetric NAT: We are behind a symmetric NAT which will make our 'from' address look differently when we talk to multiple people.");
//                updateNetworkStatus(NetworkStatus.BLOCKED);
//                break;
//            case HOSED:
//                LOG.warning("Unable to open UDP port for I2P - Port Conflict. Verify another instance of I2P is not running.");
//                updateNetworkStatus(NetworkStatus.PORT_CONFLICT);
//                break;
//            default: {
//                LOG.warning("Not connected to I2P Network.");
//                updateNetworkStatus(NetworkStatus.DISCONNECTED);
//            }
//        }
//        if(getNetworkState().networkStatus==NetworkStatus.CONNECTED && sessions.size()==0) {
//            LOG.info("Network Connected and no Sessions.");
//            if(routerContext.commSystem().isInStrictCountry()) {
//                LOG.warning("This peer is in a 'strict' country defined by I2P.");
//            }
//            if(routerContext.router().isHidden()) {
//                LOG.warning("I2P Router is in Hidden mode. I2P Service setting for hidden mode: "+config.getProperty("ra.i2p.hidden"));
//            }
//            LOG.info("Establishing Session to speed up future outgoing messages...");
//            establishSession(null, true);
//        }
    }

    public void checkRouterStats() {
//        if(routerContext==null)
//            return; // Router not yet established
//        CommSystemFacade.Status reportedStatus = getRouterStatus();
//        if(i2pRouterStatus != reportedStatus) {
//            // Status changed
//            i2pRouterStatus = reportedStatus;
//            LOG.info("I2P Router Status changed to: "+i2pRouterStatus.name());
//            reportRouterStatus();
//        }
    }

    private Integer activePeersCount() {
        return 0;
    }

    private Boolean unreachable(NetworkPeer networkPeer) {
        if(networkPeer==null || networkPeer.getDid().getPublicKey().getAddress()==null) {
            LOG.warning("Network Peer with address is required to determine if peer is unreachable.");
            return false;
        }
//        I2PSession session = establishSession("default", true);
//        Destination dest = session.lookupDest(networkPeer.getDid().getPublicKey().getAddress());
//        return routerContext.commSystem().wasUnreachable(dest.getHash());
        return false;
    }

    private Boolean inStrictCountry() {
//        return routerContext.commSystem().isInStrictCountry();
        return false;
    }

    private Boolean inStrictCountry(NetworkPeer networkPeer) {
        if(networkPeer==null || networkPeer.getDid().getPublicKey().getAddress()==null) {
            LOG.warning("Network Peer with address is required to determine if peer is in strict country.");
            return false;
        }
//        I2PSession session = establishSession("default", true);
//        Destination dest = session.lookupDest(networkPeer.getDid().getPublicKey().getAddress());
//        return routerContext.commSystem().isInStrictCountry(dest.getHash());
        return false;
    }

    private Boolean backlogged(NetworkPeer networkPeer) {
        if(networkPeer==null || networkPeer.getDid().getPublicKey().getAddress()==null) {
            LOG.warning("Network Peer with address is required to determine if peer is backlogged.");
            return false;
        }
//        I2PSession session = establishSession("default", true);
//        Destination dest = session.lookupDest(networkPeer.getDid().getPublicKey().getAddress());
//        return routerContext.commSystem().isBacklogged(dest.getHash());
        return false;
    }

    private Boolean established(NetworkPeer networkPeer) {
        if(networkPeer==null || networkPeer.getDid().getPublicKey().getAddress()==null) {
            LOG.warning("Network Peer with address is required to determine if peer is established.");
            return false;
        }
//        I2PSession session = establishSession("default", true);
//        Destination dest = session.lookupDest(networkPeer.getDid().getPublicKey().getAddress());
//        return routerContext.commSystem().isEstablished(dest.getHash());
        return false;
    }

    private String country(NetworkPeer networkPeer) {
        if(networkPeer==null || networkPeer.getDid().getPublicKey().getAddress()==null) {
            LOG.warning("Network Peer with address is required to determine country of peer.");
            return "NoPeer";
        }
//        I2PSession session = establishSession("default", true);
//        Destination dest = session.lookupDest(networkPeer.getDid().getPublicKey().getAddress());
//        return routerContext.commSystem().getCountry(dest.getHash());
        return "Unknown";
    }

    public static void main(String[] args) {
        MessageProducer messageProducer = new MessageProducer() {
            @Override
            public boolean send(Envelope envelope) {
                LOG.info(envelope.toJSON());
                return true;
            }

            @Override
            public boolean send(Envelope envelope, Client client) {
                LOG.info(envelope.toJSON());
                return true;
            }

            @Override
            public boolean deadLetter(Envelope envelope) {
                LOG.warning("Dead letter: \n\t"+envelope.toJSON());
                return false;
            }
        };
        I2PAndroidService service = new I2PAndroidService(messageProducer, null);
        service.start(Config.loadFromMainArgs(args));
        while(true) {
            Wait.aSec(1);
        }
    }

}
