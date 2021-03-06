package net.blueberrymc.installer;

import net.blueberrymc.installer.gui.InstallingPanel;
import net.blueberrymc.installer.gui.MainPanel;
import util.maven.Dependency;
import util.maven.MavenRepository;
import util.maven.MavenRepositoryFetcher;
import util.maven.Repository;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

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
        if (frame != null) {
            frame.add(MAIN_PANEL);
            frame.validate();
        }
        if (headless) {
            if (args.length == 0 || (!args[0].equals("install-client") && !args[0].equals("install-server") && !args[0].equals("extract"))) {
                System.out.println("Usage: java -jar blueberry-installer.jar install-client ... Install the client in .minecraft directory");
                System.out.println("       java -jar blueberry-installer.jar install-server ... Install the server in current directory");
                System.out.println("       java -jar blueberry-installer.jar extract ... Extract files in the installer in current directory");
                System.exit(1);
            }
            if (args[0].equals("install-client")) {
                InstallOptions.installType = InstallType.INSTALL_CLIENT;
                InstallOptions.installDir = InstallOptions.getMinecraftDir();
                if (!InstallOptions.installDir.exists()) {
                    throw new RuntimeException("minecraft data directory does not exist");
                }
            }
            if (args[0].equals("install-server")) {
                InstallOptions.installType = InstallType.INSTALL_SERVER;
            }
            if (args[0].equals("extract")) {
                InstallOptions.installType = InstallType.EXTRACT;
            }
            doInstall();
        }
    }

    public static JFrame initFrame(int width, int height) {
        if (headless) return null;
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            System.out.println("L&F Initialization failed, using default theme.");
            e.printStackTrace();
        }
        JFrame frame = new JFrame();
        frame.setVisible(true);
        frame.setSize(width, height);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setTitle("Mod System Installer");
        return frame;
    }

    public static void doInstall() {
        if (frame != null && !headless) {
            frame.setVisible(false);
            frame = initFrame(500, 400);
            assert frame != null;
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.add(INSTALLING_PANEL);
            frame.validate();
        }
        System.out.println("Install options:");
        System.out.println(" - Type: " + InstallOptions.installType.name());
        if (InstallOptions.installType == InstallType.INSTALL_CLIENT) {
            installClient();
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
                    try (FileOutputStream out = new FileOutputStream(new File(InstallOptions.installDir, s))) {
                        out.getChannel().transferFrom(Channels.newChannel(in), 0, Long.MAX_VALUE);
                    }
                    System.out.println("Extracted: " + s);
                } catch (IOException e) {
                    System.out.println("Error: Could not extract " + s);
                    e.printStackTrace();
                    hasError = true;
                }
            }
            if (hasError) {
                complete(true);
                return;
            }
            if (InstallOptions.installType == InstallType.INSTALL_SERVER) {
                downloadLibraries();
            } else {
                complete(false);
            }
            return;
        }
        System.out.println("Unknown action: " + InstallOptions.installType.name());
        complete(true);
    }

    public static void installClient() {
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
                out.close();
            } catch (IOException e) {
                System.out.println("Error: Could not extract " + s + " -> " + filename);
                e.printStackTrace();
                complete(true);
                return;
            }
        }
        new Thread(() -> {
            System.out.println("Patching the jar");
            INSTALLING_PANEL.status.setText("Patching");
            try {
                File clientJar = new File(version, name + ".jar");
                Process process = Runtime.getRuntime().exec("java -Dblueberry.nogui=true -Dblueberry.patchOnly=true -jar " + (clientJar.getAbsolutePath()), null, version);
                new RedirectingInputStream(process.getInputStream()).start();
                new RedirectingInputStream(process.getErrorStream()).start();
                process.waitFor(10, TimeUnit.MINUTES);
                process.destroyForcibly().waitFor(1, TimeUnit.MINUTES);
                File patchedJar;
                try (Stream<Path> stream = Files.find(
                        new File(version, "cache").toPath(),
                        1,
                        (path, basicFileAttributes) -> path.toFile().isFile() && path.toFile().getName().startsWith("patched_") && path.toFile().getName().endsWith(".jar"))) {
                    patchedJar = stream.findFirst().map(Path::toFile).orElse(null);
                }
                if (patchedJar != null) {
                    System.out.println("Deleting " + clientJar.getAbsolutePath());
                    Files.deleteIfExists(clientJar.toPath());
                    System.out.println("Moving " + patchedJar.getAbsolutePath() + " -> " + clientJar.getAbsolutePath());
                    Files.move(patchedJar.toPath(), clientJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
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
    }

    public static void downloadLibraries() {
        System.out.println(ProfileData.serverLibraries.size() + " defined libraries to download");
        if (ProfileData.serverLibraries.isEmpty()) {
            return;
        }
        MavenRepository maven = new MavenRepository();
        maven.addRepository(Repository.mavenLocal());
        maven.addRepository(Repository.mavenCentral());
        for (int i = 0; i < ProfileData.serverRepositories.size(); i++) {
            maven.addRepository(Repository.url("maven-repo-" + i, ProfileData.serverRepositories.get(i)));
        }
        for (String dependency : ProfileData.serverLibraries) {
            maven.addDependency(Dependency.resolve(dependency));
        }
        MavenRepositoryFetcher fetcher = maven.newFetcher(new File(InstallOptions.installDir, "libraries")).withMessageReporter((message, error) -> {
            if (error == null) {
                System.out.println(message);
            } else {
                if (message != null) {
                    System.err.println(message);
                }
                error.printStackTrace();
            }
        });
        INSTALLING_PANEL.status.setText("Collecting libraries");
        INSTALLING_PANEL.progress.setValue(0);
        Function<Consumer<Object>, Integer> work = (publish) -> {
            Set<Dependency> toDownload = new HashSet<>();
            for (Dependency dependency : maven.getDependencies()) {
                toDownload.addAll(fetcher.collectAllDependencies(dependency));
            }
            for (Dependency dependency : maven.getDependencies()) {
                toDownload.removeIf(dep -> dep.getGroupId().equals(dependency.getGroupId()) && dep.getArtifactId().equals(dependency.getArtifactId()));
            }
            toDownload.addAll(maven.getDependencies());
            for (String s : ProfileData.serverLibrariesExclude) {
                toDownload.removeIf(dependency -> dependency.toNotation().matches(s));
            }
            System.out.println("Attempting to download " + toDownload.size() + " libraries");
            INSTALLING_PANEL.status.setText("Downloading libraries");
            INSTALLING_PANEL.progress.setMaximum(toDownload.size());
            INSTALLING_PANEL.progress.setValue(0);
            List<Dependency> toDownloadList = Collections.synchronizedList(new ArrayList<>(toDownload));
            for (int i = 0; i < toDownloadList.size(); i++) {
                Dependency dependency = toDownloadList.get(i);
                System.out.println("[" + (i + 1) + "/" + toDownloadList.size() + "] Downloading " + dependency.toNotation());
                publish.accept(i);
                fetcher.downloadFile(dependency);
            }
            return toDownloadList.size();
        };
        if (headless) {
            work.apply(o -> {});
            complete(false);
        } else {
            new SwingWorker<>() {
                @Override
                protected Object doInBackground() {
                    return work.apply(this::publish);
                }

                @Override
                protected void process(List<Object> chunks) {
                    for (Object o : chunks) {
                        int i = (int) o;
                        INSTALLING_PANEL.progress.setValue(i + 1);
                    }
                }

                @Override
                protected void done() {
                    complete(false);
                }
            }.execute();
        }
    }

    public static void complete(boolean error) {
        INSTALLING_PANEL.status.setText("Completed");
        if (frame != null) {
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        }
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
