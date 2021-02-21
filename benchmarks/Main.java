package za.ac.sun.cs.regex;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import org.apache.commons.cli.*;

import java.io.*;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.*;

public class Main {

    private static class Report {
        private String time;
        private String memoryUsed;
        private String error;

        public void setTime(String time) {
            this.time = time;
        }

        public void setMemoryUsed(String memoryUsed) {
            this.memoryUsed = memoryUsed;
        }

        public void setError(String error) {
            this.error = error;
        }

        public void print() {
            if (error != null) {
                System.out.println(error);
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(time == null ? "NA" : time);
                sb.append(" ");
                sb.append(memoryUsed == null ? "NA" : memoryUsed);
                System.out.println(sb.toString());
            }
        }
    }

    private static class PatternString {
        private String pattern;
        private String string;

        public PatternString(String pattern, String string) {
            this.pattern = pattern;
            this.string = string;
        }
    }

    private static final Type PATTERN_STRING_TYPE = new TypeToken<List<PatternString>>() {}.getType();

    private static List<PatternString> readJsonFromFile(String dataFile) {
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

    private static void processPatternString(PatternString patternString, Report report) {
        final long TIMEOUT = 3;

        final Runnable tryMatch = new Thread(() -> {
            long startTime = System.nanoTime();

            Pattern pattern = Pattern.compile(patternString.pattern);
            Matcher matcher = pattern.matcher(patternString.string);
            matcher.matches();

            long endTime = System.nanoTime();
            long duration = (endTime - startTime);
            double seconds = (double)duration / 1_000_000_000.0;
            if (seconds < (double)TIMEOUT) {
                report.setTime(String.valueOf(seconds));
            }
        });

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future future = executor.submit(tryMatch);
        executor.shutdown(); /* This does not cancel the already-scheduled task. */

        try {
            future.get(TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            report.setError("InterruptedException");
        } catch (ExecutionException ee) {
            report.setError("ExecutionException");
        } catch (TimeoutException te) {
            report.setError("TimeoutException");
            future.cancel(true);
            executor.shutdownNow();
        }

        if (!executor.isTerminated()) {
            executor.shutdownNow(); // If you want to stop the code that hasn't finished.
        }
    }

    private static void runBenchmarks(List<PatternString> data) {
        for (PatternString ps : data) {
            runBenchmark(ps);
        }
    }

    private static void runBenchmark(PatternString ps) {
        Report report = new Report();
        processPatternString(ps, report);
        report.print();
    }

    private static boolean isFile(String pathToFile) {
        File f = new File(pathToFile);
        return f.exists() && !f.isDirectory();
    }

    public static Options createOptions() {
        Option jsonFile = Option.builder()
                .desc("path to json file with regexes and input string")
                .hasArg()
                .longOpt("jsonfile")
                .build();

        Option regex = Option.builder()
                .desc("regex to test")
                .hasArg()
                .longOpt("regex")
                .build();

        Option inputString = Option.builder()
                .desc("input string to match with regex")
                .hasArg()
                .longOpt("input")
                .build();

        Options options = new Options();
        options.addOption(jsonFile);
        options.addOption(regex);
        options.addOption(inputString);
        return options;
    }

    public static void main(String[] args) {
        Options options = createOptions();

        CommandLineParser parser = new DefaultParser();
        CommandLine line = null;
        try {
            line = parser.parse(options, args);
        } catch (ParseException exp) {
            System.out.println(exp.getMessage());
            System.exit(1);
        }

        if (line.hasOption("jsonfile")) {
            String jsonFile = line.getOptionValue("jsonfile");
            if (!isFile(jsonFile)) {
                System.err.printf("'%s' is not a file!\n", jsonFile);
                System.exit(2);
            }
            List<PatternString> data = readJsonFromFile(jsonFile);
            runBenchmarks(data);
        } else if (line.hasOption("regex") && line.hasOption("input")) {
            String regex = line.getOptionValue("regex");
            String inputString = line.getOptionValue("input");
            runBenchmark(new PatternString(regex, inputString));
        } else {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("java-regex-engine", options);
        }
    }

}
