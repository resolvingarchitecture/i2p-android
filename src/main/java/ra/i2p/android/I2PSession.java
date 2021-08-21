package ra.i2p.android;

import ra.common.Envelope;
import ra.common.network.*;
import ra.common.route.ExternalRoute;

import java.util.*;
import java.util.logging.Logger;

class I2PSession extends BaseClientSession {

    private static final Logger LOG = Logger.getLogger(I2PSession.class.getName());

    // I2CP parameters allowed in the config file
    // Undefined parameters use the I2CP defaults
    private static final String PARAMETER_I2CP_DOMAIN_SOCKET = "i2cp.domainSocket";
    private static final List<String> I2CP_PARAMETERS = Arrays.asList(new String[] {
            PARAMETER_I2CP_DOMAIN_SOCKET,
            "inbound.length",
            "inbound.lengthVariance",
            "inbound.quantity",
            "inbound.backupQuantity",
            "outbound.length",
            "outbound.lengthVariance",
            "outbound.quantity",
            "outbound.backupQuantity",
    });

    private boolean isTest = false;

    protected I2PAndroidService service;
    protected boolean connected = false;
    protected String address;

    public I2PSession(I2PAndroidService service) {
        this.service = service;
    }

    public String getAddress() {
        return address;
    }

    /**
     * Initializes session properties
     */
    @Override
    public boolean init(Properties p) {
        super.init(p);
        LOG.info("Initializing I2P Session....");

        LOG.info("I2P Session initialized.");
        return true;
    }

    /**
     * Open a Socket with internal router.
     * I2P Service currently uses only one internal I2P address thus ignoring any address passed to this method.
     */
    @Override
    public boolean open(String i2pAddress) {
        LOG.info("Opening connection...");
        NetworkPeer localI2PPeer = service.getNetworkState().localPeer;
        // read the local destination key from the key file if it exists
        String alias = "anon";
        if(localI2PPeer!=null && localI2PPeer.getDid().getUsername()!=null) {
            alias = localI2PPeer.getDid().getUsername();
        }

        // TODO: Verify local I2P Router is connected to I2P network.

        service.getNetworkState().localPeer = localI2PPeer;
        LOG.info("Local I2P Peer Address in base64: " + localI2PPeer.getDid().getPublicKey().getAddress());
        LOG.info("Local I2P Peer Fingerprint (hash) in base64: " + localI2PPeer.getDid().getPublicKey().getFingerprint());
        // Update Peer Manager
//        Envelope pEnv = Envelope.documentFactory();
//        DLC.addContent(localI2PPeer, pEnv);
//        DLC.addRoute("ra.peermanager.PeerManagerService","UPDATE_PEER", pEnv);
//        service.send(pEnv);
        return true;
    }

    /**
     * Connect to I2P network
     * @return
     */
    @Override
    public boolean connect() {
//        if(!isOpen()) {
//            LOG.info("No Socket Manager open.");
//            open(null);
//        }
        LOG.info("I2P Session connecting...");
        long start = System.currentTimeMillis();
        try {
            // Throws I2PSessionException if the connection fails
            connect();
            connected = true;
        } catch (Exception e) {
            LOG.warning(e.getLocalizedMessage());
            return false;
        }
        long end = System.currentTimeMillis();
        long durationMs = end - start;
        LOG.info("I2P Session connected. Took "+(durationMs/1000)+" seconds.");
        return true;
    }

//    public boolean isOpen() {
//        return socketManager!=null;
//    }

    @Override
    public boolean disconnect() {
        connected = false;
        return true;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public boolean close() {
        connected = false;
        return true;
    }

    @Override
    public Boolean send(Envelope envelope) {
        if (envelope == null) {
            LOG.warning("No Envelope.");
            return false;
        }
        if(!(envelope.getRoute() instanceof ExternalRoute)) {
            LOG.warning("Not an external route.");
            envelope.getMessage().addErrorMessage("Route must be external.");
            return false;
        }
        ExternalRoute er = (ExternalRoute)envelope.getRoute();
        if (er.getDestination() == null) {
            LOG.warning("No Destination Peer for I2P found in while sending to I2P.");
            envelope.getMessage().addErrorMessage("Code:" + ExternalRoute.DESTINATION_PEER_REQUIRED+", Destination Peer Required.");
            return false;
        }
        if (!Network.I2P.name().equals(er.getDestination().getNetwork())) {
            LOG.warning("Not an envelope for I2P.");
            envelope.getMessage().addErrorMessage("Code:" + ExternalRoute.DESTINATION_PEER_WRONG_NETWORK+", Not meant for I2P Network.");
            return false;
        }

        LOG.info("Sending Envelope id: "+envelope.getId().substring(0,7)+"... to: "+er.getDestination().getDid().getPublicKey().getFingerprint().substring(0,7)+"...");
        String content = envelope.toJSON();
        LOG.fine("Content to send: \n\t" + content);
        if (content.length() > 31500) {
            // Just warn for now
            // TODO: Split into multiple serialized packets
            LOG.warning("Content longer than 31.5kb. May have issues.");
        }
        // TODO: Send to I2P Router
        return true;
    }

}
