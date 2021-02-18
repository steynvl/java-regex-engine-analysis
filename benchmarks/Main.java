package za.ac.sun.cs.regex;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.*;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.*;

public class Main {

    private class PatternString {
        private String pattern;
        private String string;
        private String jdk8;
        private String jdk9;
        private String jdk11;

        public PatternString(String pattern, String string, String jdk8,
                             String jdk9, String jdk11) {
            this.pattern = pattern;
            this.string = string;
            this.jdk8 = jdk8;
            this.jdk9 = jdk9;
            this.jdk11 = jdk11;
        }
    }

    private static final Type PATTERN_STRING_TYPE = new TypeToken<List<PatternString>>() {}.getType();

    private static List<PatternString> readData(String dataFile) {
        Gson gson = new Gson();
        JsonReader reader = null;
        try {
            reader = new JsonReader(new FileReader(dataFile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return gson.fromJson(reader, PATTERN_STRING_TYPE);
    }

    private static void writeData(List<PatternString> data, String dataFile) {
        try (Writer writer = new FileWriter(dataFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void addTimeToAppropriateField(int javaVersion,
                                                  PatternString ps,
                                                  String time) {
        switch (javaVersion) {
            case 8:
                ps.jdk8 = time;
                break;
            case 9:
                ps.jdk9 = time;
                break;
            case 11:
                ps.jdk11 = time;
                break;
            default:
                System.out.println("Unsupported Java Version: " + javaVersion);
                System.exit(2);
                break;
        }
    }

    private static void processPatternString(int javaVersion, PatternString patternString) {
        final long TIMEOUT = 30;

        final Runnable tryMatch = new Thread(() -> {
            long startTime = System.nanoTime();

            Pattern pattern = Pattern.compile(patternString.pattern);
            Matcher matcher = pattern.matcher(patternString.string);
            matcher.matches();

            long endTime = System.nanoTime();
            long duration = (endTime - startTime);
            double seconds = (double)duration / 1_000_000_000.0;
            if (seconds < (double)TIMEOUT) {
                addTimeToAppropriateField(javaVersion, patternString, String.valueOf(seconds));
            }
        });

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future future = executor.submit(tryMatch);
        executor.shutdown(); /* This does not cancel the already-scheduled task. */

        try {
            future.get(TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            addTimeToAppropriateField(javaVersion, patternString, "InterruptedException");
        } catch (ExecutionException ee) {
            addTimeToAppropriateField(javaVersion, patternString, "ExecutionException");
        } catch (TimeoutException te) {
            addTimeToAppropriateField(javaVersion, patternString, "TimeoutException");
            future.cancel(true);
            executor.shutdownNow();
        }

        if (!executor.isTerminated()) {
            executor.shutdownNow(); // If you want to stop the code that hasn't finished.
        }
    }

    private static void runBenchmarks(int javaVersion, List<PatternString> data) {
        for (PatternString ps : data) {
            processPatternString(javaVersion, ps);
        }
    }

    private static boolean isDigit(String string) {
        try {
            Integer.parseInt(string);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isFile(String pathToFile) {
        File f = new File(pathToFile);
        return f.exists() && !f.isDirectory();
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java RunBenchmarks <java_version> <path_to_data.json>");
            System.exit(0);
        }

        String javaVersion = args[0];
        if (!isDigit(javaVersion)) {
            System.out.println("Invalid java version, must be a digit!");
            System.exit(3);
        }

        String dataFile = args[1];
        if (!isFile(dataFile)) {
            System.out.printf("'%s' is not a file!\n", dataFile);
            System.exit(4);
        }

        List<PatternString> data = readData(dataFile);
        runBenchmarks(Integer.parseInt(javaVersion), data);
        writeData(data, dataFile);
    }

}
