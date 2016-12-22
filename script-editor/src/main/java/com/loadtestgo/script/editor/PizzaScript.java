package com.loadtestgo.script.editor;

import com.loadtestgo.script.api.TestResult;
import com.loadtestgo.script.engine.*;
import com.loadtestgo.script.har.HarWriter;
import com.loadtestgo.util.*;
import jline.console.ConsoleReader;
import org.pmw.tinylog.Logger;

import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;

/**
 * Class containing main function.  The name of the class is what shows
 * up as the application name, so don't change the file name to Main.java
 */
public class PizzaScript {
    public static String AppName = "PizzaScript Editor";
    public static String PromptNewLine = ">> ";
    public static String PromptContinueLine = "+> ";
    public static String PromptCompleteLine = "?> ";

    static boolean guiMode = true;
    static boolean saveHar = false;
    static boolean bExiting = false;
    static String fileName = null;

    private static void processArgs(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("-")) {
                String switchName = stripLeadingDashes(arg);
                switch(switchName) {
                    case "v":
                    case "version":
                        printVersion();
                        System.exit(0);
                        break;
                    case "h":
                    case "help":
                        printHelp();
                        System.exit(0);
                        break;
                    case "c":
                    case "console":
                        guiMode = false;
                        break;
                    case "har":
                        saveHar = true;
                        break;
                }
            } else {
                if (fileName != null) {
                    printError("Only one file name can be specified.");
                    System.exit(1);
                }
                fileName = arg;
            }
        }
    }

    private static void printError(String s) {
        System.out.println(s);

        printHelp();
    }

    private static void printHelp() {
        printVersion();

        System.out.println();
        System.out.println("pizzascript-ide [options] [filename]");
        System.out.println();
        System.out.println("  -console / -c   start on the console (no gui)");
        System.out.println("  -har            save a HTTP Archive file after running console script");
        System.out.println("  -help / -h      print this help");
        System.out.println("  -version / -v   print the version number");
        System.out.println();
        System.out.println("Open the IDE:");
        System.out.println("  pizzascript-ide");
        System.out.println();
        System.out.println("Run a file:");
        System.out.println("  pizzascript-ide -console filename.js");
        System.out.println();
        System.out.println("Run a file and save HTTP Archive to 'filename.js.har':");
        System.out.println("  pizzascript-ide -har -console filename.js");
        System.out.println();
        System.out.println("Open a file in the editor:");
        System.out.println("  pizzascript-ide filename.js");
        System.out.println();
        System.out.println("Open the interactive console debugger:");
        System.out.println("  pizzascript-ide -console");
        System.out.println();
    }

    private static void printVersion() {
        System.out.println(String.format("%s: %s", AppName, PizzaScript.getVersion()));
    }

    private static String stripLeadingDashes(String arg) {
        if (arg == null) {
            return null;
        }

        if (arg.length() == 0) {
            return arg;
        }

        if (arg.charAt(0) == '-') {
            int pos = 1;
            if (arg.length() >= 2) {
                if (arg.charAt(1) == '-') {
                    pos = 2;
                }
            }
            return arg.substring(pos);
        }

        return arg;
    }

    public static void main(String[] args) {
        processArgs(args);

        // Make sure the settings are loaded from the current directory
        // before before a Swing GUI dialog changes it
        Settings settings = IniFile.loadSettings();
        if (!settings.has(EngineSettings.CAPTURE_VIDEO)) {
            settings.set(EngineSettings.CAPTURE_VIDEO, false);
        }

        if (guiMode) {
            if (Os.isMac()) {
                System.setProperty("apple.laf.useScreenMenuBar", "true");
                System.setProperty("apple.eawt.quitStrategy", "CLOSE_ALL_WINDOWS");
            }

            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                Logger.error(e, "Unable set system look and feel");
            }

            Gui editor = new Gui();
            if (fileName != null) {
                editor.openFile(fileName);
            }
        } else {
            boolean success;

            EasyTestContext testContext = new EasyTestContext();
            JavaScriptEngine engine = new JavaScriptEngine();
            engine.init(testContext);

            registerExitFunction(engine);

            if (fileName != null) {
                File scriptFile  = new File(fileName);
                testContext.setBaseDirectory(Path.getParentDirectory(scriptFile));
                success = processFile(fileName, scriptFile, engine);
            } else {
                success = interactiveMode(engine);
            }

            engine.finish();

            if (saveHar) {
                TestResult testResult = testContext.getTestResult();
                String harFile = "results.har";
                if (StringUtils.isSet(fileName)) {
                    harFile = fileName += ".har";
                }
                try {
                    System.out.println(String.format("Saving HAR file %s...", harFile));
                    HarWriter.save(testResult, harFile);
                } catch (IOException e) {
                    System.out.println(String.format("Unable to save har file: %s", e.getMessage()));
                }
            }

            System.exit(success ? 0 : 1);
        }
    }

    private static boolean interactiveMode(JavaScriptEngine engine) {
        try {
            ConsoleReader reader = new ConsoleReader();
            reader.setPrompt(PromptNewLine);
            reader.addCompleter(new JavaScriptCompleter(engine));
            reader.setBellEnabled(false);
            reader.setHistoryEnabled(true);

            int lineNo = 1;
            int startLine = lineNo;

            String line;
            String source = "";

            PrintWriter out = new PrintWriter(reader.getOutput());

            boolean bContinueReading = false;

            while (!bExiting && (line = reader.readLine()) != null ) {
                source = source + line + "\n";
                lineNo++;

                bContinueReading = !engine.stringIsCompilableUnit(source);

                if (!bContinueReading) {
                    try {
                        Object result = engine.runPartialScript(source, startLine);
                        if (result != null) {
                            out.println(engine.valueToString(result));
                        }
                    } catch (ScriptException se) {
                        out.println(se.getMessage());
                    }
                    startLine = lineNo;
                    source = "";
                }

                String prompt;
                if (bContinueReading) {
                    prompt = PromptContinueLine;
                } else {
                    prompt = PromptNewLine;
                }
                reader.setPrompt(prompt);
            }
            return true;
        } catch (IOException e) {
            System.err.println(e.getMessage());
            return false;
        }
    }

    private static boolean processFile(String filename, File scriptFile, JavaScriptEngine engine) {
        try {
            if (!scriptFile.exists()) {
                throw new FileNotFoundException("Unable to find file '" + filename + "'");
            }
            String scriptContexts = FileUtils.readAllText(filename);
            if (scriptContexts == null) {
                throw new IOException("Error reading '" + filename + "'");
            }
            Object result = engine.runScript(scriptContexts, filename);
            if (result != null) {
                System.out.println(engine.valueToString(result));
            }
            return true;
        } catch (IOException|ScriptException e) {
            System.err.println(e.getMessage());
            return false;
        }
    }

    private static void registerExitFunction(JavaScriptEngine engine) {
        try {
            java.lang.reflect.Method method = PizzaScript.class.getMethod("exit");
            engine.registerStaticFunction("exit", method);
            engine.registerStaticFunction("quit", method);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public static void exit() {
        bExiting = true;
    }

    public static String getVersion() {
        Package thisPackage = PizzaScript.class.getPackage();
        String version = thisPackage.getImplementationVersion();
        if (version == null) {
            version = "dev";
        }
        return version;
    }
}

