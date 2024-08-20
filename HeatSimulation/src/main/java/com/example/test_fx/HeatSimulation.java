package com.example.test_fx;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import javax.swing.*;
import java.util.Random;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

public class HeatSimulation extends Application {

    // define the width and height of the frame
    private static int frameWidth = 800;
    private static int frameHeight = 600;
    private static int PIXEL_SIZE = 10;
    public static Color[] heatColors; // array to store color gradient for heat visualization
    public static double[][] cellTemperature; // array to store the temperature of each cell
    public static boolean[][] fixedPoints; // array to mark fixed heat points
    public static int heatPoints = 10; // number of heat points
    public static boolean start = false; // flag to start simulation
    private static boolean showGraphicalInterface = true; // flag to toggle graphical interface
    private static boolean useParallel = false; // flag to toggle parallel computation
    private static final double STABILITY_THRESHOLD = 0.25; // threshold for temperature stability
    public static final int FPS = 60; // frames per second for animation
    public static long runtime = 0; // variable to track runtime

    private static final AtomicBoolean computationRunning = new AtomicBoolean(false); // flag to check if computation is running
    private static ForkJoinPool forkJoinPool; // fork/join pool for parallel computation

    // initializes the grid with temperature values and fixed points
    public static void initialize() {
        int gridWidth = frameWidth / PIXEL_SIZE;
        int gridHeight = frameHeight / PIXEL_SIZE;

        cellTemperature = new double[gridWidth][gridHeight];
        fixedPoints = new boolean[gridWidth][gridHeight];

        // initialize the grid with temperature 0
        for (int i = 0; i < gridWidth; i++) {
            for (int j = 0; j < gridHeight; j++) {
                cellTemperature[i][j] = 0;
            }
        }

        // generate random heat sources
        Random random = new Random(89211208);

        for (int i = 0; i < heatPoints; i++) {
            int x = random.nextInt(gridWidth); // random x-coordinate
            int y = random.nextInt(gridHeight); // random y-coordinate
            cellTemperature[x][y] = 100; // set temperature of heat point
            fixedPoints[x][y] = true; // mark cell as fixed heat point
        }

        // create a color gradient for visualizing heat
        heatColors = createMultiGradient(new Color[]{
                Color.BLACK,
                Color.rgb(17, 0, 92, 1),
                Color.rgb(47, 0, 128, 1),
                Color.rgb(166, 1, 153, 1),
                Color.rgb(191, 46, 89, 1),
                Color.rgb(231, 79, 29, 1),
                Color.rgb(253, 125, 15, 1),
                Color.rgb(255, 182, 10, 1),
                Color.rgb(255, 229, 84, 1),
                Color.rgb(252, 239, 195, 1)}, 100);
    }

    // shows a dialog box to get user input for resolution, heat points, and options
    public static void dialogBox() {
        JPanel panel = new JPanel();
        JLabel resolutionLabel = new JLabel("Enter Resolution (width x height):");
        JTextField resolutionField = new JTextField(15);

        panel.add(resolutionLabel);
        panel.add(resolutionField);

        // show the option dialog with the text field
        int result = JOptionPane.showConfirmDialog(null, panel, "Enter Resolution",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String resolutionInput = resolutionField.getText().trim();

            try {
                // split the input on "x" to get width and height
                String[] parts = resolutionInput.split("x");
                if (parts.length == 2) {
                    int width = Integer.parseInt(parts[0].trim());
                    int height = Integer.parseInt(parts[1].trim());

                    if (width > 0 && height > 0) {
                        frameWidth = width; // set frame width
                        frameHeight = height; // set frame height
                    } else {
                        JOptionPane.showMessageDialog(null, "Width and Height must be positive integers.");
                        dialogBox(); // re-show dialog if input is invalid
                        return;
                    }
                } else {
                    JOptionPane.showMessageDialog(null, "Please enter the resolution in the format: width x height");
                    dialogBox(); // re-show dialog if input format is incorrect
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "Please enter valid numbers for width and height.");
                dialogBox(); // re-show dialog if input is not valid
                return;
            }
        }

        boolean validHeatPoints = false;
        while (!validHeatPoints) {
            String input = JOptionPane.showInputDialog(null, "Enter the number of heat points:");
            heatPoints = 10; // default value

            if (input != null && !input.isEmpty()) {
                try {
                    heatPoints = Integer.parseInt(input); // parse heat points
                    validHeatPoints = true;
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(null, "Please enter a valid number.");
                }
            }
        }

        // option to show or hide graphical interface
        int interfaceChoice = JOptionPane.showConfirmDialog(null,
                "Do you want to show the graphical interface?",
                "Graphical Interface",
                JOptionPane.YES_NO_OPTION);

        showGraphicalInterface = (interfaceChoice == JOptionPane.YES_OPTION);

        // option to enable or disable parallel computation
        int parallelChoice = JOptionPane.showConfirmDialog(null,
                "Do you want to enable parallel computation?",
                "Parallel Computation",
                JOptionPane.YES_NO_OPTION);

        useParallel = (parallelChoice == JOptionPane.YES_OPTION);
        start = true; // set start flag to true
    }

    private Timeline timeline = null; // initialize timeline as null

    @Override
    public void start(Stage primaryStage) {
        if (start) {
            initialize(); // ensure initialization is always called

            if (!showGraphicalInterface) {
                // if no graphical interface is needed, run calculation and output results in the console
                System.out.println("Starting simulation in non-graphical mode...");
                runtime = System.currentTimeMillis(); // record start time
                calculate(); // perform calculation without graphical output
                System.out.println("Runtime: " + (System.currentTimeMillis() - runtime) + "ms"); // print runtime
                System.exit(0); // exit the application
                return;
            }

            Canvas canvas = new Canvas(frameWidth, frameHeight);
            GraphicsContext gc = canvas.getGraphicsContext2D();

            StackPane root = new StackPane();
            root.getChildren().add(canvas);

            primaryStage.setTitle("Heat Simulation"); // set window title
            primaryStage.setScene(new Scene(root, frameWidth, frameHeight)); // set scene with specified dimensions
            primaryStage.setResizable(false); // make window non-resizable
            primaryStage.show(); // display the stage

            // start computation and update the graphics automatically
            startComputation(gc);
        }
    }

    private void startComputation(GraphicsContext gc) {
        if (computationRunning.get()) {
            // prevent starting a new computation if one is already running
            return;
        }

        computationRunning.set(true);

        timeline = new Timeline(new KeyFrame(Duration.millis(1000 / FPS), event -> {
            boolean thresholdReached = updateAndRender(gc);

            if (thresholdReached) {
                if (timeline != null) { // ensure timeline is not null before attempting to stop it
                    timeline.stop();
                }
                computationRunning.set(false);
                System.out.println("Threshold reached. Stopping simulation.");
            }
        }));

        timeline.setCycleCount(Timeline.INDEFINITE); // run indefinitely until manually stopped
        timeline.play();
    }

    // updates the temperature grid and renders the new state
    private boolean updateAndRender(GraphicsContext gc) {
        boolean thresholdReached = true;
        double threshold = 0.1; // example threshold for temperature change

        int gridWidth = frameWidth / PIXEL_SIZE;
        int gridHeight = frameHeight / PIXEL_SIZE;

        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                // check bounds to prevent ArrayIndexOutOfBoundsException
                if (x >= 0 && x < gridWidth && y >= 0 && y < gridHeight) {
                    if (!fixedPoints[x][y]) {
                        double newTemp = calculateTemperature(x, y);
                        if (Math.abs(newTemp - cellTemperature[x][y]) > threshold) {
                            thresholdReached = false; // mark as not stable if temperature change is significant
                        }
                        cellTemperature[x][y] = newTemp;
                    }

                    // check bounds before accessing heatColors
                    int colorIndex = (int) Math.min(Math.floor(cellTemperature[x][y]), heatColors.length - 1);
                    gc.setFill(heatColors[colorIndex]);
                    gc.fillRect(x * PIXEL_SIZE, y * PIXEL_SIZE, PIXEL_SIZE, PIXEL_SIZE);
                }
            }
        }

        return thresholdReached;
    }

    // performs the heat simulation calculation
    private void calculate() {
        boolean stable = false;
        int gridWidth = frameWidth / PIXEL_SIZE;
        int gridHeight = frameHeight / PIXEL_SIZE;

        if (useParallel) {
            // parallel computation using Fork/Join
            forkJoinPool = new ForkJoinPool();
            try {
                while (!stable) {
                    stable = true;
                    double[][] newTemperatures = new double[gridWidth][gridHeight];

                    ComputeTask task = new ComputeTask(0, gridWidth, 0, gridHeight, newTemperatures);
                    forkJoinPool.invoke(task);

                    synchronized (this) {
                        if (!task.isStable()) {
                            stable = false;
                        }
                    }

                    cellTemperature = newTemperatures;
                }
            } finally {
                forkJoinPool.shutdown(); // shut down the fork/join pool
            }
        } else {
            // sequential computation
            while (!stable) {
                stable = true;
                double[][] newTemperatures = new double[gridWidth][gridHeight];
                for (int x = 0; x < gridWidth; x++) {
                    for (int y = 0; y < gridHeight; y++) {
                        if (!fixedPoints[x][y]) {
                            double newTemp = calculateTemperature(x, y);
                            if (Math.abs(newTemp - cellTemperature[x][y]) > STABILITY_THRESHOLD) {
                                stable = false; // mark as not stable if temperature change is significant
                            }
                            newTemperatures[x][y] = newTemp;
                        } else {
                            newTemperatures[x][y] = cellTemperature[x][y];
                        }
                    }
                }
                cellTemperature = newTemperatures;
            }
        }
    }

    // recursive task for parallel computation using Fork/Join
    private class ComputeTask extends RecursiveAction {
        private final int startX, endX, startY, endY;
        private final double[][] newTemperatures;
        private static final int THRESHOLD = 20; // adjust threshold as needed
        private volatile boolean stable = true;

        public ComputeTask(int startX, int endX, int startY, int endY, double[][] newTemperatures) {
            this.startX = startX;
            this.endX = endX;
            this.startY = startY;
            this.endY = endY;
            this.newTemperatures = newTemperatures;
        }

        @Override
        protected void compute() {
            if ((endX - startX) * (endY - startY) <= THRESHOLD) {
                // perform computation on small enough task
                computeDirectly();
            } else {
                // split task into smaller tasks
                int midX = (startX + endX) / 2;
                int midY = (startY + endY) / 2;

                invokeAll(
                        new ComputeTask(startX, midX, startY, midY, newTemperatures),
                        new ComputeTask(midX, endX, startY, midY, newTemperatures),
                        new ComputeTask(startX, midX, midY, endY, newTemperatures),
                        new ComputeTask(midX, endX, midY, endY, newTemperatures)
                );
            }
        }

        private void computeDirectly() {
            for (int x = startX; x < endX; x++) {
                for (int y = startY; y < endY; y++) {
                    if (!fixedPoints[x][y]) {
                        double newTemp = calculateTemperature(x, y);
                        if (Math.abs(newTemp - cellTemperature[x][y]) > STABILITY_THRESHOLD) {
                            stable = false; // mark as not stable if temperature change is significant
                        }
                        newTemperatures[x][y] = newTemp;
                    } else {
                        newTemperatures[x][y] = cellTemperature[x][y];
                    }
                }
            }
        }

        public boolean isStable() {
            return stable;
        }
    }

    // draw the current state of the simulation
    private void draw(GraphicsContext gc) {
        int gridWidth = frameWidth / PIXEL_SIZE;
        int gridHeight = frameHeight / PIXEL_SIZE;

        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                int colorIndex = (int) Math.min(Math.floor(cellTemperature[x][y]), heatColors.length - 1);
                gc.setFill(heatColors[colorIndex]);
                gc.fillRect(x * PIXEL_SIZE, y * PIXEL_SIZE, PIXEL_SIZE, PIXEL_SIZE);
            }
        }
    }

    // entry point for the application
    public static void main(String[] args) {
        dialogBox(); // show dialog box for user input
        if (start) {
            launch(args); // launch the JavaFX application if start flag is true
        }
    }

    // create a gradient between two colors
    public static Color[] createGradient(final Color one, final Color two, final int numSteps) {
        double r1 = one.getRed();
        double g1 = one.getGreen();
        double b1 = one.getBlue();
        double a1 = one.getOpacity();

        double r2 = two.getRed();
        double g2 = two.getGreen();
        double b2 = two.getBlue();
        double a2 = two.getOpacity();

        double newR = 0;
        double newG = 0;
        double newB = 0;
        double newA = 0;

        Color[] gradient = new Color[numSteps];
        double iNorm;
        for (int i = 0; i < numSteps; i++) {
            iNorm = i / (double) numSteps;
            newR = r1 + iNorm * (r2 - r1);
            newG = g1 + iNorm * (g2 - g1);
            newB = b1 + iNorm * (b2 - b1);
            newA = a1 + iNorm * (a2 - a1);
            gradient[i] = new Color(newR, newG, newB, newA);
        }

        return gradient;
    }

    // create a multi-color gradient
    public static Color[] createMultiGradient(Color[] colors, int numSteps) {
        int numSections = colors.length - 1;
        int gradientIndex = 0;
        Color[] gradient = new Color[numSteps];
        Color[] temp;

        if (numSections <= 0) {
            throw new IllegalArgumentException("You must pass in at least 2 colors in the array!");
        }

        for (int section = 0; section < numSections; section++) {
            temp = createGradient(colors[section], colors[section + 1], numSteps / numSections);
            for (int i = 0; i < temp.length; i++) {
                gradient[gradientIndex++] = temp[i];
            }
        }

        if (gradientIndex < numSteps) {
            for (; gradientIndex < numSteps; gradientIndex++) {
                gradient[gradientIndex] = colors[colors.length - 1];
            }
        }

        return gradient;
    }

    // calculate the new temperature based on neighboring cells
    public static double calculateTemperature(int x, int y) {
        double temp = 0;
        int count = 0;

        if (x > 0) {
            temp += cellTemperature[x - 1][y];
            count++;
        }
        if (x < frameWidth / PIXEL_SIZE - 1) {
            temp += cellTemperature[x + 1][y];
            count++;
        }
        if (y > 0) {
            temp += cellTemperature[x][y - 1];
            count++;
        }
        if (y < frameHeight / PIXEL_SIZE - 1) {
            temp += cellTemperature[x][y + 1];
            count++;
        }

        temp /= count; // calculate average temperature from neighbors
        return temp;
    }
}