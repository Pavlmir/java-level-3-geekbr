package lesson4.network.Server;

import static lesson4.utils.Share.*;

public class ResourceCleaner extends Thread {
    private Cleaner cleaner;

    public ResourceCleaner(Cleaner cleaner) {
        this.cleaner = cleaner;
        this.setDaemon(true);
        this.start();
    }

    @Override
    public void run() {

        while (true) {
            try {
                Thread.sleep(secToMsec(CLEANER_TIMEOUT));
            } catch (InterruptedException e) {e.printStackTrace();}

            cleaner.scheduledCleaning();
        }
    }
}
