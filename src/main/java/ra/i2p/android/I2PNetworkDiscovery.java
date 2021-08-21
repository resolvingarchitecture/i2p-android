package ra.i2p.android;

import ra.common.Envelope;
import ra.common.network.NetworkPeer;
import ra.common.network.NetworkStatus;
import ra.common.tasks.BaseTask;
import ra.common.tasks.TaskRunner;

import java.util.Date;
import java.util.logging.Logger;

public class I2PNetworkDiscovery extends BaseTask {

    private static final Logger LOG = Logger.getLogger(I2PNetworkDiscovery.class.getName());

    private I2PAndroidService service;

    public I2PNetworkDiscovery(I2PAndroidService service, TaskRunner taskRunner) {
        super(I2PNetworkDiscovery.class.getSimpleName(), taskRunner);
        this.service = service;
    }

    @Override
    public Boolean execute() {
        if(service.getNetworkState().networkStatus == NetworkStatus.CONNECTED
                && service.getNumberPeers() < service.getMaxPeers()) {
            if(service.inflightTimers.size()>0) {
                LOG.warning(service.inflightTimers.size()+" in-flight timer(s) timed out.");
                synchronized (service.inflightTimers) {
                    service.inflightTimers.clear();
                }
            }
            if(service.getNumberPeers()==0) {
                LOG.warning("Must have a peer to start the discovery process. Waiting for a peer to connect...");
            } else {
                NetworkPeer toPeer = service.getRandomPeer();
                Envelope e = Envelope.documentFactory();
                service.inflightTimers.put(e.getId(), new Date().getTime());
                e.addContent(service.getPeers());
                e.addExternalRoute(I2PAndroidService.class, I2PAndroidService.OPERATION_SEND, service.getNetworkState().localPeer, toPeer);
                e.mark("NetOpReq");
                service.sendOut(e);
            }
        }
        return true;
    }


}
