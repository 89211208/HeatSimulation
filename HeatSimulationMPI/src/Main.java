import mpi.MPI;
import mpi.MPIException;

import java.util.Random;

public class Main {

    // define the grid dimensions and the number of heat points
    private static int gridWidth = 80;
    private static int gridHeight = 60;
    private static int heatPoints = 10;
    private static double[][] cellTemperature; // stores temperature of each cell in the grid
    private static boolean[][] fixedPoints; // indicates if a cell is a fixed heat point
    private static final double STABILITY_THRESHOLD = 0.25; // threshold for stability check
    private static final int MAX_ITERATIONS = 100000; // maximum number of iterations for convergence

    // initializes the grid with random heat points
    public static void initialize(int gridWidth, int gridHeight, int heatPoints) {
        cellTemperature = new double[gridWidth][gridHeight];
        fixedPoints = new boolean[gridWidth][gridHeight];

        Random rand = new Random(89211208);
        for (int i = 0; i < heatPoints; i++) {
            int x = rand.nextInt(gridWidth); // random x-coordinate
            int y = rand.nextInt(gridHeight); // random y-coordinate
            cellTemperature[x][y] = 100; // set initial temperature of heat point
            fixedPoints[x][y] = true; // mark the cell as a fixed point
        }
    }

    // computes the heat distribution across the grid
    public static void computeHeatDistribution() {
        int rank, size;
        try {
            rank = MPI.COMM_WORLD.Rank(); // get the rank of the current process
            size = MPI.COMM_WORLD.Size(); // get the total number of processes
        } catch (MPIException e) {
            e.printStackTrace();
            return;
        }

        // determine the range of rows each process will handle
        int rowsPerProcess = gridHeight / size;
        int startRow = rank * rowsPerProcess;
        int endRow = (rank == size - 1) ? gridHeight : (rank + 1) * rowsPerProcess;

        // local arrays for storing temperatures and fixed points
        double[] localTemperatures = new double[gridWidth * rowsPerProcess];
        boolean[] localFixedPoints = new boolean[gridWidth * rowsPerProcess];
        double[] gatheredTemperatures = new double[gridWidth * gridHeight];

        if (rank == 0) {
            // flatten 2D arrays for scattering
            double[] flattenedTemperatures = flatten2DArray(cellTemperature);
            boolean[] flattenedFixedPoints = flatten2DArray(fixedPoints);

            try {
                // scatter temperatures and fixed points to all processes
                MPI.COMM_WORLD.Scatter(flattenedTemperatures, 0, rowsPerProcess * gridWidth, MPI.DOUBLE,
                        localTemperatures, 0, rowsPerProcess * gridWidth, MPI.DOUBLE, 0);
                MPI.COMM_WORLD.Scatter(flattenedFixedPoints, 0, rowsPerProcess * gridWidth, MPI.BOOLEAN,
                        localFixedPoints, 0, rowsPerProcess * gridWidth, MPI.BOOLEAN, 0);
            } catch (MPIException e) {
                e.printStackTrace();
            }
        } else {
            try {
                // receive scattered data for non-root processes
                MPI.COMM_WORLD.Scatter(null, 0, 0, MPI.DOUBLE,
                        localTemperatures, 0, rowsPerProcess * gridWidth, MPI.DOUBLE, 0);
                MPI.COMM_WORLD.Scatter(null, 0, 0, MPI.BOOLEAN,
                        localFixedPoints, 0, rowsPerProcess * gridWidth, MPI.BOOLEAN, 0);
            } catch (MPIException e) {
                e.printStackTrace();
            }
        }

        boolean stable = false;
        int iterations = 0;

        // iterate until the system reaches stability or the maximum number of iterations
        while (!stable && iterations < MAX_ITERATIONS) {
            stable = true;
            double[] newTemperatures = new double[gridWidth * rowsPerProcess];

            // update temperatures for the current segment of the grid
            for (int x = 0; x < gridWidth; x++) {
                for (int y = 0; y < rowsPerProcess; y++) {
                    int globalY = startRow + y;
                    if (!localFixedPoints[x + y * gridWidth]) {
                        double newTemp = calculateTemperature(x, globalY, localTemperatures, gridWidth, rowsPerProcess);
                        if (Math.abs(newTemp - localTemperatures[x + y * gridWidth]) > STABILITY_THRESHOLD) {
                            stable = false; // system is not stable
                        }
                        newTemperatures[x + y * gridWidth] = newTemp;
                    } else {
                        newTemperatures[x + y * gridWidth] = localTemperatures[x + y * gridWidth];
                    }
                }
            }

            try {
                // gather updated temperatures from all processes
                MPI.COMM_WORLD.Gather(newTemperatures, 0, rowsPerProcess * gridWidth, MPI.DOUBLE,
                        gatheredTemperatures, 0, rowsPerProcess * gridWidth, MPI.DOUBLE, 0);
            } catch (MPIException e) {
                e.printStackTrace();
            }

            if (rank == 0) {
                // update the global temperature array with gathered data
                cellTemperature = unflatten2DArray(gatheredTemperatures, gridWidth, gridHeight);
            }

            iterations++;
            System.out.println("Rank " + rank + " completed iteration " + iterations);
        }

        if (rank == 0) {
            System.out.println("Computation finished in " + iterations + " iterations.");
            printResults(); // print the final temperature distribution
        }
    }

    // flattens a 2D array to a 1D array
    private static double[] flatten2DArray(double[][] array) {
        int width = array.length;
        int height = array[0].length;
        double[] flattened = new double[width * height];
        for (int i = 0; i < width; i++) {
            System.arraycopy(array[i], 0, flattened, i * height, height);
        }
        return flattened;
    }

    // flattens a 2D boolean array to a 1D array
    private static boolean[] flatten2DArray(boolean[][] array) {
        int width = array.length;
        int height = array[0].length;
        boolean[] flattened = new boolean[width * height];
        for (int i = 0; i < width; i++) {
            System.arraycopy(array[i], 0, flattened, i * height, height);
        }
        return flattened;
    }

    // converts a 1D array back to a 2D array
    private static double[][] unflatten2DArray(double[] array, int width, int height) {
        double[][] unflattened = new double[width][height];
        for (int i = 0; i < width; i++) {
            System.arraycopy(array, i * height, unflattened[i], 0, height);
        }
        return unflattened;
    }

    // prints the final temperature grid
    public static void printResults() {
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                System.out.printf("%5.1f ", cellTemperature[x][y]);
            }
            System.out.println();
        }
    }

    // calculates the new temperature of a cell based on its neighbors
    public static double calculateTemperature(int x, int y, double[] temperatures, int gridWidth, int rowsPerProcess) {
        double temp = 0;
        int count = 0;

        // add temperature of neighboring cells
        if (x > 0) {
            temp += temperatures[(x - 1) + (y % rowsPerProcess) * gridWidth];
            count++;
        }
        if (x < gridWidth - 1) {
            temp += temperatures[(x + 1) + (y % rowsPerProcess) * gridWidth];
            count++;
        }
        if (y > 0) {
            temp += temperatures[x + ((y - 1) % rowsPerProcess) * gridWidth];
            count++;
        }
        if (y < rowsPerProcess - 1) {
            temp += temperatures[x + ((y + 1) % rowsPerProcess) * gridWidth];
            count++;
        }

        // calculate the average temperature
        temp /= count;
        return temp;
    }

    public static void main(String[] args) {
        try {
            MPI.Init(args); // initialize MPI environment

            int rank = MPI.COMM_WORLD.Rank(); // get the rank of the current process
            int size = MPI.COMM_WORLD.Size(); // get the total number of processes

            if (rank == 0) {
                initialize(gridWidth, gridHeight, heatPoints); // initialize grid on root process
            }

            // broadcast grid parameters to all processes
            int[] params = new int[3];
            if (rank == 0) {
                params[0] = gridWidth;
                params[1] = gridHeight;
                params[2] = heatPoints;
            }
            MPI.COMM_WORLD.Bcast(params, 0, 3, MPI.INT, 0);
            gridWidth = params[0];
            gridHeight = params[1];
            heatPoints = params[2];

            computeHeatDistribution(); // compute heat distribution

            MPI.Finalize(); // finalize MPI environment
        } catch (MPIException e) {
            e.printStackTrace();
        }
    }
}