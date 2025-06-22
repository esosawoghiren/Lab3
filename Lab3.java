import java.util.concurrent.Semaphore;

public class Lab3 {
  final static int PORT0 = 0;
  final static int PORT1 = 1;
  final static int MAXLOAD = 5;

  public static void main(String args[]) {
    final int NUM_CARS = 10;
    int i;

    Ferry fer = new Ferry(PORT0, 10);

    Auto[] automobile = new Auto[NUM_CARS];
    for (i = 0; i < 7; i++) {
      automobile[i] = new Auto(i, PORT0, fer);
    }
    for (; i < NUM_CARS; i++) {
      automobile[i] = new Auto(i, PORT1, fer);
    }

    Ambulance ambulance = new Ambulance(PORT0, fer);

    fer.start();
    for (i = 0; i < NUM_CARS; i++) {
      automobile[i].start();
    }
    ambulance.start();

    try {
      fer.join();
    } catch (InterruptedException e) {}

    System.out.println("Ferry stopped.");
    for (i = 0; i < NUM_CARS; i++) {
      automobile[i].interrupt();
    }
    ambulance.interrupt();
  }
}

class Auto extends Thread {
    private int id_auto;
    private int port;
    private Ferry fry;

    public Auto(int id, int prt, Ferry ferry) {
        this.id_auto = id;
        this.port = prt;
        this.fry = ferry;
    }

    public void run() {
        while (true) {
            try {
                sleep((int) (300 * Math.random()));
            } catch (Exception e) {
                break;
            }

            System.out.println("Auto " + id_auto + " arrives at port " + port);

            while (true) {
                try {
                    fry.mutex.acquire();
                    boolean samePort = fry.getPort() == port;
                    boolean notFull = fry.getLoad() < Lab3.MAXLOAD;
                    boolean notDisembarking = !fry.isDisembarking;

                    if (samePort && notFull && notDisembarking) {
                        fry.addLoad();
                        fry.mutex.release();

                        Semaphore semBoard = (port == 0) ? fry.semBoardPort0 : fry.semBoardPort1;
                        semBoard.acquire();

                        System.out.println("Auto " + id_auto + " boards on the ferry at port " + port);

                        fry.mutex.acquire();
                        if (fry.getLoad() == Lab3.MAXLOAD) {
                            fry.semDepart.release();
                        }
                        fry.mutex.release();

                        fry.semDisembark.acquire();
                        port = 1 - port;

                        System.out.println("Auto " + id_auto + " disembarks from ferry at port " + port);
                        fry.reduceLoad();
                        fry.vehicleDisembarked();
                        break;
                    } else {
                        fry.mutex.release();
                        Thread.sleep(50);
                    }
                } catch (InterruptedException e) {
                    return;
                }
            }

            if (isInterrupted()) break;
        }
        System.out.println("Auto " + id_auto + " terminated");
    }
}

class Ambulance extends Thread {
  private int port;
  private Ferry fry;

  public Ambulance(int prt, Ferry ferry) {
    this.port = prt;
    this.fry = ferry;
  }

  public void run() {
    while (true) {
      try {
        sleep((int) (1000 * Math.random()));
      } catch (Exception e) {
        break;
      }

      System.out.println("Ambulance arrives at port " + port);

      while (true) {
        try {
          fry.mutex.acquire();
          boolean samePort = fry.getPort() == port;
          boolean notFull = fry.getLoad() < Lab3.MAXLOAD;
          boolean notDisembarking = !fry.isDisembarking;

          if (samePort && notFull && notDisembarking) {
            fry.addLoad();
            fry.mutex.release();

            Semaphore semBoard = (port == 0) ? fry.semBoardPort0 : fry.semBoardPort1;
            semBoard.acquireUninterruptibly();

            System.out.println("Ambulance boards the ferry at port " + port);
            fry.semDepart.release();

            fry.semDisembark.acquireUninterruptibly();
            port = 1 - port;

            System.out.println("Ambulance disembarks the ferry at port " + port);
            fry.reduceLoad();
            fry.vehicleDisembarked();
            break;
          } else {
            fry.mutex.release();
            Thread.sleep(50);
          }
        } catch (InterruptedException e) {
          return;
        }
      }

      if (isInterrupted()) break;
    }
    System.out.println("Ambulance terminates.");
  }
}

class Ferry extends Thread {
    private int port = 0;
    private int load = 0;
    private int numCrossings;

    public Semaphore semBoardPort0;
    public Semaphore semBoardPort1;
    public Semaphore semDisembark;
    public Semaphore semDepart;

    public Semaphore mutex = new Semaphore(1, true);
    public Semaphore disembarkingDone = new Semaphore(0, true);
    public boolean isDisembarking = false;
    private int disembarkCount = 0;

    public Ferry(int prt, int nbtours) {
        semBoardPort0 = new Semaphore(0, true);
        semBoardPort1 = new Semaphore(0, true);
        semDisembark = new Semaphore(0, true);
        semDepart = new Semaphore(0, true);
        this.port = prt;
        numCrossings = nbtours;
    }

    public void run() {
        System.out.println("Start at port " + port + " with a load of " + load + " vehicles");

        if (port == 0) {
            semBoardPort0.release(Lab3.MAXLOAD);
        } else {
            semBoardPort1.release(Lab3.MAXLOAD);
        }

        for (int i = 0; i < numCrossings; i++) {
            semDepart.acquireUninterruptibly();

            System.out.println("Departure from port " + port + " with a load of " + load + " vehicles");
            System.out.println("Crossing " + i + " with a load of " + load + " vehicles");

            try { sleep((int) (100 * Math.random())); } catch (Exception e) {}

            port = 1 - port;
            System.out.println("Arrive at port " + port + " with a load of " + load + " vehicles");

            isDisembarking = true;
            disembarkCount = load;
            semDisembark.release(load);

            while (disembarkCount > 0) {
                disembarkingDone.acquireUninterruptibly();
            }

            isDisembarking = false;
            if (port == 0) semBoardPort0.release(Lab3.MAXLOAD);
            else semBoardPort1.release(Lab3.MAXLOAD);
        }
    }

    public int getPort() { return port; }
    public int getLoad() { return load; }
    public void addLoad() { load++; }
    public void reduceLoad() { load--; }

    public void vehicleDisembarked() {
        disembarkCount--;
        disembarkingDone.release();
    }
}
