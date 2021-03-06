package lesson5;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

public class Car implements Runnable {
    private static int CARS_COUNT;
    private static int WINNER_CAR = -1;

    static {
        CARS_COUNT = 0;
    }

    private Race race;
    private int speed;
    private int carNo;
    private static ReentrantLock lock = new ReentrantLock();
    private String name;
    private CountDownLatch barrier;

    public String getName() {
        return name;
    }

    public int getSpeed() {
        return speed;
    }

    public Car(Race race, int speed, CountDownLatch cb) {
        this.barrier = cb;
        this.race = race;
        this.speed = speed;
        CARS_COUNT++;
        this.carNo = CARS_COUNT;
        this.name = "Участник #" + CARS_COUNT;
    }

    @Override
    public void run() {
        try {
            System.out.println(this.name + " готовится");
            Thread.sleep(500 + (int) (Math.random() * 800));
            barrier.countDown();
            System.out.println(this.name + " готов");

            barrier.await();
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (int i = 0; i < race.getStages().size(); i++) {
            race.getStages().get(i).go(this);
        }

        lock.lock();
        if (WINNER_CAR == -1) {
            WINNER_CAR = carNo;
            System.out.println("ВАЖНОЕ ОБЪЯВЛЕНИЕ >>> Победитель: участник #" + carNo);
        }
        lock.unlock();
    }
}