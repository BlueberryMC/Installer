package util.maven;

public interface MessageReporter {
    void onError(String message, Throwable throwable);
}
