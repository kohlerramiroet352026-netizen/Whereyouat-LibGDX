package com.whereyouat.lwjgl3;

import org.lwjgl.system.macosx.LibC;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds some utilities to ensure that the JVM was started with the
 * {@code -XstartOnFirstThread} argument, which is required on macOS for LWJGL 3
 * to function. Also helps on Windows when starting with some JVMs that
 * don't support LWJGL 3 well without explicit setup.
 * <br>
 * Based on https://github.com/spencer-lord/StartupHelper
 */
public class StartupHelper {

    private static final String JVM_RESTARTED_ARG = "jvmIsRestarted";

    private StartupHelper() {
        throw new UnsupportedOperationException();
    }

    /**
     * Starts a new JVM if the application was started on macOS without the
     * {@code -XstartOnFirstThread} argument. This also handles some other
     * edge cases on various platforms.
     *
     * @return whether a new JVM was started and thus no code should be executed.
     *         Be sure to test for this and exit the application immediately if
     *         this method returns true!
     */
    public static boolean startNewJvmIfRequired(boolean redirectOutput) {
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("mac")) {
            return false;
        }

        // Check if JVM is already restarted
        if ("true".equals(System.getProperty(JVM_RESTARTED_ARG))) {
            return false;
        }

        // Check -XstartOnFirstThread
        long pid = LibC.getpid();

        // Check whether -XstartOnFirstThread is already present
        List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        boolean hasStartOnFirstThread = false;
        for (String jvmArg : jvmArgs) {
            if (jvmArg.contains("-XstartOnFirstThread")) {
                hasStartOnFirstThread = true;
                break;
            }
        }

        if (hasStartOnFirstThread) {
            return false;
        }

        // Restart the JVM with -XstartOnFirstThread
        String separator = System.getProperty("file.separator");
        String classpath = System.getProperty("java.class.path");
        String mainClass = System.getenv("JAVA_MAIN_CLASS_" + pid);
        if (mainClass == null) {
            StackTraceElement[] trace = Thread.currentThread().getStackTrace();
            if (trace.length > 0) {
                mainClass = trace[trace.length - 1].getClassName();
            } else {
                System.err.println("The main class could not be determined.");
                return false;
            }
        }

        // Build command to start a new JVM with -XstartOnFirstThread
        ArrayList<String> jvmArgsNew = new ArrayList<String>();
        String javaExec = System.getProperty("java.home") + separator + "bin" + separator + "java";
        jvmArgsNew.add(javaExec);
        jvmArgsNew.add("-XstartOnFirstThread");
        jvmArgsNew.add("-D" + JVM_RESTARTED_ARG + "=true");
        jvmArgsNew.addAll(jvmArgs);
        jvmArgsNew.add("-cp");
        jvmArgsNew.add(classpath);
        jvmArgsNew.add(mainClass);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(jvmArgsNew);
            Process process = processBuilder.start();
            if (redirectOutput) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("There was an error restarting the JVM");
            e.printStackTrace();
        }

        return false;
    }

    public static boolean startNewJvmIfRequired() {
        return startNewJvmIfRequired(true);
    }
}

