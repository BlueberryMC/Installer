package net.blueberrymc.installer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class RedirectingInputStream extends Thread {
    private final InputStream in;

    public RedirectingInputStream(InputStream in) {
        super("RedirectingInputStream");
        setDaemon(true);
        this.in = in;
    }

    @Override
    public void run() {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = br.readLine()) != null) System.out.println(line);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
