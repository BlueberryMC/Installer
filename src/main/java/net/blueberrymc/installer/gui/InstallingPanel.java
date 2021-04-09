package net.blueberrymc.installer.gui;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.PrintStream;

public class InstallingPanel extends JPanel {
    public final JLabel status = new JLabel("Installing...");
    public final JProgressBar progress = new JProgressBar();

    public InstallingPanel() {
        this.setLayout(new FlowLayout(FlowLayout.CENTER, 10000000, 5));
        this.add(status);
        this.add(progress);
        JTextArea textField = new JTextArea(16, 58);
        ((DefaultCaret) textField.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        hookStdout(System.out, textField);
        textField.setEditable(false);
        JScrollPane pane = new JScrollPane(textField);
        pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        this.add(pane);
        this.validate();
    }

    public static void hookStdout(PrintStream out, JTextArea textField) {
        System.setOut(new PrintStream(out, true) {
            @Override
            public void print(int i) {
                textField.append(i + "\n");
                super.print(i);
            }

            @Override
            public void print(char c) {
                textField.append(c + "\n");
                super.print(c);
            }

            @Override
            public void print(long l) {
                textField.append(l + "\n");
                super.print(l);
            }

            @Override
            public void print(boolean b) {
                textField.append(b + "\n");
                super.print(b);
            }

            @Override
            public void print(float f) {
                textField.append(f + "\n");
                super.print(f);
            }

            @Override
            public void print(char[] s) {
                StringBuilder str = new StringBuilder();
                for (char c : s) str.append(c);
                textField.append(str + "\n");
                super.print(s);
            }

            @Override
            public void print(double d) {
                textField.append(d + "\n");
                super.print(d);
            }

            @Override
            public void print(String s) {
                textField.append(s + "\n");
                super.print(s);
            }

            @Override
            public void print(Object obj) {
                textField.append(obj + "\n");
                super.print(obj);
            }
        });
    }
}
