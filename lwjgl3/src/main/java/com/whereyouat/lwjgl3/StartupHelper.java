package com.whereyouat.lwjgl3;

import org.lwjgl.system.macosx.LibC;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

public class StartupHelper {
    private static final String JVM_RESTARTED_ARG = "jvmIsRestarted";

    private StartupHelper() {
        throw new UnsupportedOperationException();
    }

    public static boolean startNewJvmIfRequired(boolean redirectOutput) {
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("mac")) {
            return false;
        }

        if ("true".equals(System.getProperty(JVM_RESTARTED_ARG))) {
            return false;
        }

        long pid = LibC.getpid();

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
