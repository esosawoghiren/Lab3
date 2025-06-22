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

    /* Start the threads */

    fer.start();   //Start the ferry thread 
    for (i = 0; i < NUM_CARS; i++) {
      automobile[i].start();   //start the automobile thread
    }
    ambulance.start();  //start the ambulance thread

    try {
      fer.join();
    } catch (InterruptedException e) {}  // Wait until ferry terminates.

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
                    fry.mutex.acquire();   // Lock access to shared ferry state

                     // Check if ferry is at the same port, not full, and not disembarking
                    boolean samePort = fry.getPort() == port;
                    boolean notFull = fry.getLoad() < Lab3.MAXLOAD;
                    boolean notDisembarking = !fry.isDisembarking;

                    if (samePort && notFull && notDisembarking) {
                        // Safe to board: increment load and unlock access
                        fry.addLoad();
                        fry.mutex.release();

                         // Wait for boarding permission from the ferry (ensures order)
                        Semaphore semBoard = (port == 0) ? fry.semBoardPort0 : fry.semBoardPort1;
                        semBoard.acquire();

                        System.out.println("Auto " + id_auto + " boards on the ferry at port " + port);

                        fry.mutex.acquire();

                         // If this is the last vehicle needed to fill the ferry, release departure signal
                        if (fry.getLoad() == Lab3.MAXLOAD) {
                            fry.semDepart.release();
                        }
                        fry.mutex.release();
                        
                         // Wait to be told it's time to disembark
                        fry.semDisembark.acquire();

                        // Update to new port after crossing
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
            fry.semDepart.release(); // Priority: Depart immediately

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

    public Semaphore mutex = new Semaphore(1, true);      // Mutual exclusion for accessing shared state
    public Semaphore disembarkingDone = new Semaphore(0, true);   // Tracks number of disembarked vehicles
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

    // Tracks number of disembarked vehicles
    
    public void run() {
        System.out.println("Start at port " + port + " with a load of " + load + " vehicles");

        if (port == 0) {
        // Release permits for up to 5 vehicles at port 0
            semBoardPort0.release(Lab3.MAXLOAD); 
        } else {
            semBoardPort1.release(Lab3.MAXLOAD);
        }

        for (int i = 0; i < numCrossings; i++) {
            semDepart.acquireUninterruptibly();  // Release permits for up to 5 vehicles at port 0

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

    // Called by a vehicle after disembarking to notify the ferry
    public void vehicleDisembarked() {
        disembarkCount--;
        disembarkingDone.release();   // Signal ferry that one vehicle has exited
    }
}
