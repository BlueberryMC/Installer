package net.blueberrymc.installer;

import java.io.File;
import java.util.Locale;

public class InstallOptions {
    public static InstallType installType = InstallType.INSTALL_CLIENT;
    public static File installDir = new File(".");

    public static File getMinecraftDir() {
        String userHomeDir = System.getProperty("user.home", ".");
        String osType = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        String mcDir = ".minecraft";
        if (osType.contains("win") && System.getenv("APPDATA") != null) {
            return new File(System.getenv("APPDATA"), mcDir);
        } else if (osType.contains("mac")) {
            return new File(new File(new File(userHomeDir, "Library"), "Application Support"), "minecraft");
        }
        return new File(userHomeDir, mcDir);
    }
}
