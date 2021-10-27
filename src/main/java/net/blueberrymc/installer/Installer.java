package net.blueberrymc.installer;

import net.blueberrymc.installer.gui.InstallingPanel;
import net.blueberrymc.installer.gui.MainPanel;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Installer {
    public static final boolean headless =
            GraphicsEnvironment.isHeadless() || Boolean.getBoolean("net.blueberrymc.installer.nogui");
    public static final boolean failed;

    static {
        boolean fail = true;
        try {
            ProfileData.read();
            fail = false;
        } catch (IOException e) {
            System.out.println("Failed to read profile data");
            e.printStackTrace();
            if (!headless) JOptionPane.showMessageDialog(null, "Failed to read profile data", "Error", JOptionPane.ERROR_MESSAGE);
        }
        String path = Installer.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (path.contains("!/")) {
            if (headless) JOptionPane.showMessageDialog(null, "Do not run this jar in a folder ending with !", "Error", JOptionPane.ERROR_MESSAGE);
            System.out.println("Do not run this jar in a folder ending with !");
            System.out.println(path);
            fail = true;
        }
        failed = fail;
    }

    public static JFrame frame = initFrame(500, 220);
    public static final MainPanel MAIN_PANEL = new MainPanel();
    public static final InstallingPanel INSTALLING_PANEL = new InstallingPanel();

    public static void main(String[] args) {
        if (failed) return;
        System.out.println("Loaded profile:");
        System.out.println(" - Name: " + ProfileData.name);
        System.out.println(" - Hide client: " + ProfileData.hideClient);
        System.out.println(" - Hide server: " + ProfileData.hideServer);
        System.out.println(" - Hide extract: " + ProfileData.hideExtract);
        if (!ProfileData.hideServer && ProfileData.serverFiles.isEmpty()) {
            System.out.println("Warning: hideServer is false but serverFiles is empty.");
        }
        if (!ProfileData.hideExtract && ProfileData.extractFiles.isEmpty()) {
            System.out.println("Warning: hideExtract is false but extractFiles is empty.");
        }
        frame.add(MAIN_PANEL);
        frame.validate();
    }

    public static JFrame initFrame(int width, int height) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            System.out.println("L&F Initialization failed, using default theme.");
            e.printStackTrace();
        }
        JFrame frame = new JFrame();
        if (!headless) frame.setVisible(true);
        frame.setSize(width, height);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setTitle("Mod System Installer");
        return frame;
    }

    public static void doInstall() {
        frame.setVisible(false);
        frame = initFrame(500, 400);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.add(INSTALLING_PANEL);
        frame.validate();
        System.out.println("Install options:");
        System.out.println(" - Type: " + InstallOptions.installType.name());
        if (InstallOptions.installType == InstallType.INSTALL_CLIENT) {
            File versions = new File(InstallOptions.installDir, "versions");
            if (!versions.exists() || !versions.isDirectory()) {
                System.out.println("Error: " + versions.getAbsolutePath() + " does not exist or not a directory.");
                complete(true);
                return;
            }
            String name = ProfileData.name;
            File version = new File(versions, name);
            if (version.exists() && !version.isDirectory()) {
                System.out.println("Error: " + version.getAbsolutePath() + " is not a directory.");
                complete(true);
                return;
            }
            if (version.exists()) {
                System.out.println("Found existing directory, we're re-using it: " + version.getAbsolutePath());
            } else {
                System.out.println("Creating directory: " + version.getAbsolutePath());
                if (!version.mkdir()) {
                    System.out.println("Error: Failed to create directory: " + version.getAbsolutePath());
                    complete(true);
                    return;
                }
            }
            INSTALLING_PANEL.progress.setMaximum(2);
            for (String s : Arrays.asList("client.jar", "client.json")) {
                INSTALLING_PANEL.status.setText("Extracting: " + s);
                INSTALLING_PANEL.progress.setValue(INSTALLING_PANEL.progress.getValue() + 1);
                String filename;
                if (s.equals("client.jar")) {
                    filename = name + ".jar";
                } else {
                    filename = name + ".json";
                }
                System.out.println("Extracting: " + s + " -> " + filename);
                try (InputStream in = Installer.class.getClassLoader().getResourceAsStream(s)) {
                    if (in == null) {
                        System.out.println("Error: Could not find: " + s);
                        complete(true);
                        return;
                    }
                    FileOutputStream out = new FileOutputStream(new File(version, filename));
                    out.getChannel().transferFrom(Channels.newChannel(in), 0, Long.MAX_VALUE);
                    System.out.println("Extracted: " + s + " -> " + filename);
                } catch (IOException e) {
                    System.out.println("Error: Could not extract " + s + " -> " + filename);
                    e.printStackTrace();
                    complete(true);
                    return;
                }
            }
            new Thread(() -> {
                INSTALLING_PANEL.status.setText("Patching");
                try {
                    Process process = Runtime.getRuntime().exec("java -Dblueberry.nogui=true -Dblueberry.patchOnly=true -jar " + (new File(version, name + ".jar").getAbsolutePath()), null, InstallOptions.installDir);
                    new RedirectingInputStream(process.getInputStream()).start();
                    new RedirectingInputStream(process.getErrorStream()).start();
                    process.waitFor(10, TimeUnit.MINUTES);
                } catch (IOException | InterruptedException e) {
                    System.out.println("Warning: Failed to run patcher");
                    e.printStackTrace();
                } catch (Throwable throwable) {
                    System.gc();
                    throwable.printStackTrace();
                    complete(true);
                    return;
                }
                complete(false);
            }).start();
            return;
        }
        if (InstallOptions.installType == InstallType.EXTRACT || InstallOptions.installType == InstallType.INSTALL_SERVER) {
            boolean hasError = false;
            List<String> toExtract;
            if (InstallOptions.installType == InstallType.INSTALL_SERVER) {
                toExtract = ProfileData.serverFiles;
            } else {
                toExtract = ProfileData.extractFiles;
            }
            if (toExtract.isEmpty()) {
                System.out.println("Warning: Nothing to extract");
            }
            INSTALLING_PANEL.progress.setMaximum(toExtract.size());
            for (String s : toExtract) {
                INSTALLING_PANEL.status.setText("Extracting: " + s);
                INSTALLING_PANEL.progress.setValue(INSTALLING_PANEL.progress.getValue() + 1);
                System.out.println("Extracting: " + s);
                try (InputStream in = Installer.class.getClassLoader().getResourceAsStream(s)) {
                    if (in == null) {
                        System.out.println("Error: Could not find: " + s);
                        hasError = true;
                        continue;
                    }
                    FileOutputStream out = new FileOutputStream(new File(InstallOptions.installDir, s));
                    out.getChannel().transferFrom(Channels.newChannel(in), 0, Long.MAX_VALUE);
                    System.out.println("Extracted: " + s);
                } catch (IOException e) {
                    System.out.println("Error: Could not extract " + s);
                    e.printStackTrace();
                    hasError = true;
                }
            }
            // TODO: Download libraries when installing server
            complete(hasError);
            return;
        }
        System.out.println("Unknown action: " + InstallOptions.installType.name());
        complete(true);
    }

    public static void complete(boolean error) {
        INSTALLING_PANEL.status.setText("Completed");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        INSTALLING_PANEL.progress.setValue(INSTALLING_PANEL.progress.getMaximum());
        if (error) {
            System.out.println("An error occurred while installing");
            if (!headless) {
                JOptionPane.showMessageDialog(null, "An error has occurred and was not fully able to complete the installation. Click OK to close the window, and you can see the logs.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            System.out.println("Done!");
            if (!headless) {
                int i = JOptionPane.showConfirmDialog(null, "Install completed successfully.\nIf you installed the client, please re-launch the your launcher again if it's already open.\nClick Yes to close the installer, Click No to see logs.", "Completed", JOptionPane.YES_NO_OPTION);
                if (i == 0) System.exit(0);
            }
        }
    }
}
