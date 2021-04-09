package net.blueberrymc.installer.gui;

import net.blueberrymc.installer.InstallOptions;
import net.blueberrymc.installer.InstallType;
import net.blueberrymc.installer.Installer;
import net.blueberrymc.installer.ProfileData;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.util.Objects;

public class MainPanel extends JPanel {
    public final JLabel status = new JLabel(" ");
    public final JTextField file = new JTextField(InstallOptions.getMinecraftDir().getAbsolutePath());
    public final JButton install = new JButton("Install");

    public MainPanel() {
        this.setLayout(new FlowLayout(FlowLayout.CENTER, 10000000, 5));
        this.add(new JLabel("Welcome to mod system installer. (" + ProfileData.name + ")"));

        JPanel installOptionsPanel = new JPanel();
        installOptionsPanel.setLayout(new BoxLayout(installOptionsPanel, BoxLayout.Y_AXIS));
        installOptionsPanel.setAlignmentX(CENTER_ALIGNMENT);
        installOptionsPanel.setAlignmentY(CENTER_ALIGNMENT);
        JRadioButton installClient = new JRadioButton("Install client", true);
        JRadioButton installServer = new JRadioButton("Install server");
        JRadioButton extract = new JRadioButton("Extract");
        if (ProfileData.hideClient) {
            installClient.setEnabled(false);
            installClient.setSelected(false);
            if (!ProfileData.hideServer) {
                InstallOptions.installType = InstallType.INSTALL_SERVER;
                installServer.setSelected(true);
            }
            if (!ProfileData.hideExtract) {
                InstallOptions.installType = InstallType.EXTRACT;
                extract.setSelected(true);
            }
        }
        if (ProfileData.hideServer || ProfileData.serverFiles.isEmpty()) installServer.setEnabled(false);
        if (ProfileData.hideExtract || ProfileData.extractFiles.isEmpty()) extract.setEnabled(false);
        installClient.addActionListener(e -> {
            InstallOptions.installType = InstallType.INSTALL_CLIENT;
            file.setText(InstallOptions.getMinecraftDir().getAbsolutePath());
            fileChanged();
        });
        installServer.addActionListener(e -> {
            InstallOptions.installType = InstallType.INSTALL_SERVER;
            file.setText(new File(".").getAbsolutePath());
            fileChanged();
        });
        extract.addActionListener(e -> {
            InstallOptions.installType = InstallType.EXTRACT;
            file.setText(new File(".").getAbsolutePath());
            fileChanged();
        });
        ButtonGroup bg = new ButtonGroup();
        bg.add(installClient);
        bg.add(installServer);
        bg.add(extract);
        installOptionsPanel.add(installClient);
        installOptionsPanel.add(installServer);
        installOptionsPanel.add(extract);
        this.add(installOptionsPanel);

        JPanel fileSelectionPanel = new JPanel();
        fileSelectionPanel.setLayout(new BoxLayout(fileSelectionPanel, BoxLayout.X_AXIS));
        file.setPreferredSize(new Dimension(400, 10));
        file.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                fileChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                fileChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                fileChanged();
            }
        });
        fileSelectionPanel.add(file);
        JButton fileSelectButton = new JButton("...");
        fileSelectButton.addActionListener(e -> {
            JFileChooser dirChooser = new JFileChooser();
            dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            dirChooser.setFileHidingEnabled(false);
            dirChooser.ensureFileIsVisible(InstallOptions.installDir);
            dirChooser.setSelectedFile(InstallOptions.installDir);
            int res = dirChooser.showOpenDialog(this);
            if (res == JFileChooser.APPROVE_OPTION) {
                file.setText(dirChooser.getSelectedFile().getAbsolutePath());
                fileChanged();
            }
        });
        fileSelectionPanel.add(fileSelectButton);
        this.add(fileSelectionPanel);

        this.add(status);

        JPanel actionsPanel = new JPanel();
        actionsPanel.setLayout(new FlowLayout());
        actionsPanel.setAlignmentX(CENTER_ALIGNMENT);
        actionsPanel.setAlignmentY(CENTER_ALIGNMENT);
        if (ProfileData.hideClient && ProfileData.hideServer && ProfileData.hideExtract) {
            status.setForeground(Color.RED);
            status.setText("Cannot install because all options above are disabled");
            System.err.println("Cannot install because all options above are disabled");
            install.setEnabled(false);
            fileSelectButton.setEnabled(false);
            file.setEnabled(false);
        } else {
            install.addActionListener(e -> {
                fileChanged();
                if (!install.isEnabled()) return;
                Installer.doInstall();
            });
        }
        JButton close = new JButton("Close");
        close.addActionListener(e -> System.exit(0));
        install.setPreferredSize(new Dimension(80, 25));
        close.setPreferredSize(new Dimension(80, 25));
        actionsPanel.add(install);
        actionsPanel.add(close);
        this.add(actionsPanel);
        this.validate();
    }

    public void fileChanged() {
        status.setForeground(Color.BLACK);
        status.setText(" ");
        File f = new File(file.getText());
        if (!f.exists() || !f.isDirectory()) {
            status.setForeground(Color.RED);
            status.setText("Invalid directory");
            install.setEnabled(false);
            return;
        }
        if (InstallOptions.installType != InstallType.INSTALL_CLIENT && Objects.requireNonNull(f.listFiles()).length != 0) {
            status.setForeground(Color.MAGENTA);
            status.setText("Directory not empty");
        }
        InstallOptions.installDir = f;
        install.setEnabled(true);
    }
}
