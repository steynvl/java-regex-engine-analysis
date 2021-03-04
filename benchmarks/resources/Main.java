package za.ac.sun.cs.regex;

import com.google.common.testing.GcFinalization;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import org.apache.commons.cli.*;

import java.io.*;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.*;

public class Main {

    private static int TIMEOUT = 30;

    private static class Report {
        private PatternString patternString;
        private String time;
        private String memoryUsed;
        private String error;

        public Report(PatternString patternString) {
            this.patternString = patternString;
        }

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
            StringBuilder sb = new StringBuilder();
            sb.append("regex: ");
            sb.append(patternString.pattern);
            sb.append("\n");
            sb.append("exploit: ");
            sb.append(patternString.exploitString.toString());
            sb.append("\n");

            if (error != null) {
                sb.append(error);
            } else {
                sb.append(time == null ? "NA" : time);
                sb.append(" ");
                sb.append(memoryUsed == null ? "NA" : memoryUsed);
            }
            System.out.println(sb.toString());
        }
    }

    private static class PatternString {
        private String pattern;
        private ExploitString exploitString;

        private static class ExploitString {
            private final static int VERBATIM_CHAR_MIN = 33; /* ! */
            private final static int VERBATIM_CHAR_MAX = 126; /* ~ */

            private int degree;
            private String[] separators;
            private String[] pumps;
            private String suffix;
            private String exampleString;

            private static String visualiseString(String s) {
                StringBuilder sb = new StringBuilder();
                char sArr[] = s.toCharArray();
                for (int i = 0; i < sArr.length; i++) {
                    int c = (int) sArr[i];
                    if (c >= VERBATIM_CHAR_MIN && c <= VERBATIM_CHAR_MAX) {
                        sb.append(sArr[i]);
                    } else {
                        if (c < 256) {
                            sb.append(String.format("\\x%02x", c));
                        } else {
                            sb.append(String.format("\\x{%02x}", c));
                        }

                    }
                }
                return sb.toString();
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                if (degree <= 0) {
                    /* EDA exploit string */
                    sb.append(visualiseString(separators[0]));
                    sb.append(visualiseString(pumps[0]));
                    sb.append("...");
                    sb.append(visualiseString(pumps[0]));
                } else {
                    for (int i = 0; i < degree; i++) {
                        sb.append(visualiseString(separators[i]));
                        sb.append(visualiseString(pumps[i]));
                        sb.append("...");
                        sb.append(visualiseString(pumps[i]));
                    }
                }
                String visibleSuffix = visualiseString(suffix);
                sb.append(visibleSuffix);
                return sb.toString();

            }
        }

        private static PatternString makeWithExample(String pattern, String exampleExploitString) {
            ExploitString exploitString = new ExploitString();
            exploitString.exampleString = exampleExploitString;
            PatternString ps = new PatternString();
            ps.pattern = pattern;
            ps.exploitString = exploitString;
            return ps;
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
        final Runnable tryMatch = new Thread(() -> {
            long startTime = System.nanoTime();

            GcFinalization.awaitFullGc();
            long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            Pattern pattern = Pattern.compile(patternString.pattern);
            Matcher matcher = pattern.matcher(patternString.exploitString.exampleString);
            matcher.matches();

            GcFinalization.awaitFullGc();
            long memUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) - memBefore;
            if (memUsed < 0) {
                report.setMemoryUsed("0");
            } else {
                report.setMemoryUsed(String.valueOf(memUsed));
            }


            long duration = System.nanoTime() - startTime;
            long seconds = TimeUnit.NANOSECONDS.toSeconds(duration);
            if (seconds < (double)TIMEOUT) {
                report.setTime(String.valueOf(TimeUnit.NANOSECONDS.toMillis(duration)));
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
        Report report = new Report(ps);
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

        Option timeout = Option.builder()
                .desc("timeout after how many seconds")
                .hasArg()
                .longOpt("timeout")
                .build();

        Options options = new Options();
        options.addOption(jsonFile);
        options.addOption(regex);
        options.addOption(inputString);
        options.addOption(timeout);
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

        if (line.hasOption("timeout")) {
            try {
                TIMEOUT = Integer.parseInt(line.getOptionValue("timeout"));
                if (TIMEOUT <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                System.err.printf("'%s' is not a valid timeout number!\n", line.getOptionValue("timeout"));
                System.exit(3);
            }
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
            runBenchmark(PatternString.makeWithExample(regex, inputString));
        } else {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("java-regex-engine", options);
        }
    }

}
