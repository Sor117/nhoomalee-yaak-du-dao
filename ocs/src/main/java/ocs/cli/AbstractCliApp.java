package ocs.cli;

import ocs.domain.OcsException;

/**
 * Template method: shared argument handling and error reporting for the five commands.
 */
public abstract class AbstractCliApp {
    public void run(String[] args) {
        try {
            execute(new ArgumentValues(args));
        } catch (OcsException | IllegalArgumentException e) {
            print("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    protected abstract void execute(ArgumentValues values);
    protected void print(String line) {
        System.out.println(line);
    }
}
