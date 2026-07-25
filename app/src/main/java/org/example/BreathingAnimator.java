package org.example;

public final class BreathingAnimator {
    private boolean running;
    private double durationSeconds = 3.5;
    private long startNanos;
    private long pausedElapsedNanos;

    public void play(long nowNanos) {
        if (!running) {
            running = true;
            startNanos = nowNanos - pausedElapsedNanos;
        }
    }

    public void pause(long nowNanos) {
        if (running) {
            pausedElapsedNanos = nowNanos - startNanos;
            running = false;
        }
    }

    public void stop() {
        running = false;
        pausedElapsedNanos = 0L;
    }

    public boolean running() {
        return running;
    }

    public void setDurationSeconds(double durationSeconds) {
        this.durationSeconds = Math.max(0.25, durationSeconds);
    }

    public double durationSeconds() {
        return durationSeconds;
    }

    public double phase(long nowNanos) {
        long elapsed = running ? nowNanos - startNanos : pausedElapsedNanos;
        double cycle = elapsed / 1_000_000_000.0 / durationSeconds;
        return Math.sin(cycle * Math.PI * 2.0);
    }
}
