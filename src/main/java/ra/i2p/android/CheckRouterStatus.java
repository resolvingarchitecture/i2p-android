package ra.i2p.android;

import ra.common.tasks.BaseTask;
import ra.common.tasks.TaskRunner;

class CheckRouterStatus extends BaseTask {

    private I2PAndroidService service;

    public CheckRouterStatus(I2PAndroidService service, TaskRunner taskRunner) {
        super(CheckRouterStatus.class.getSimpleName(), taskRunner);
        this.service = service;
    }

    @Override
    public Boolean execute() {
        service.checkRouterStats();
        return true;
    }
}
