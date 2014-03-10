package pl.jgwozdz.brickcommand.engine;


import pl.jgwozdz.brickcommand.brick.Brick;
import pl.jgwozdz.brickcommand.brick.BrickEvent;
import pl.jgwozdz.brickcommand.brick.BrickEventResult;
import pl.jgwozdz.brickcommand.controller.BrickController;
import pl.jgwozdz.brickcommand.controller.ControllerEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class Engine<T extends BrickEvent, S extends BrickEventResult> {

    private final Set<? extends BrickController> inputs;
    private final Set<InputThread> inputThreads = new HashSet<>();
    private final Brick<T, S> brick;
    private Thread brickThread;
    private final Object synchro = new Object();

    public Engine(Set<? extends BrickController> inputs, Brick<T, S> brick) {
        this.brick = brick;
        this.inputs = new HashSet<>(inputs);
    }

    public Engine(BrickController input, Brick<T, S> brick) {
        this(Collections.singleton(input), brick);
    }

    public void startEngine() {
        startListeningForInputs();
        startSendingMessages();
    }

    public void stopEngine() {
        stopSendingMessages();
        stopListeningForInputs();
    }

    private void startListeningForInputs() {
        for (BrickController input : inputs) {
            InputThread thread = new InputThread(input);
            inputThreads.add(thread);
            thread.start();
        }
    }

    private void stopListeningForInputs() {
        for (Thread thread : inputThreads) {
            thread.interrupt();
        }
        for (Thread thread : inputThreads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException("Darn! Couldn't join thread " + thread.getName(), e);
            }
        }
        inputThreads.clear();
    }

    private void startSendingMessages() {
        brickThread = new OutputThread(brick);
        brickThread.start();
    }

    private void stopSendingMessages() {
        brickThread.interrupt();
        try {
            brickThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException("Darn! Couldn't join thread " + brickThread.getName(), e);
        }
    }

    protected abstract void process(ControllerEvent event);

    protected abstract T getNextBrickEvent();

    protected abstract void update(S eventResult, T sentEvent);

    private class InputThread extends Thread {
        private final BrickController controller;

        public InputThread(BrickController controller) {
            this.controller = controller;
        }

        @Override
        public void run() {
            System.out.println("starting producing thread " + this.getName());
            while (!Thread.interrupted()) {
                try {
                    ControllerEvent event = controller.waitForNextData();
                    synchronized (synchro) {
                        process(event);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            System.out.println("stopping producing thread " + this.getName());
            Thread.currentThread().interrupt();
        }
    }

    private final class OutputThread extends Thread {

        private final Brick<T, S> output;

        private OutputThread(final Brick<T, S> output) {
            this.output = output;
        }

        @Override
        public void run() {
            System.out.println("starting consuming thread");
            while (!Thread.interrupted()) {
                try {
                    T event;
                    synchronized (synchro) {
                        event = getNextBrickEvent();
                    }
                    if (event == null) { // todo make it waiting on signals
                        Thread.sleep(20);
                    } else {
//                            System.out.println(event);
                        S result = output.process(event);
                        synchronized (synchro) {
                            update(result, event);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            }
            System.out.println("stopping consuming thread");
            Thread.currentThread().interrupt();
        }
    }
}
