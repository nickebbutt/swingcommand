package swingcommand;

import junit.framework.Assert;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: nick
 * Date: 25-Apr-2009
 * Time: 12:05:02
 * To change this template use File | Settings | File Templates.
 */
public class TestBackgroundTaskErrorInDoInBackground extends AbstractCommandTest {

    private BackgroundTask<String> task;

    public void testBackgroundTaskErrorInBackgroundFromBackgroundThread() {
        doTest();
        checkEndStates();
    }

    public void testBackgroundTaskErrorInBackgroundFromEventThread() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                doTest();
            }
        });
        checkEndStates();
    }

    private void checkEndStates() {
        waitForLatch();
        assertOrdering(8, "end");

        assertEquals(ExecutionState.ERROR, task.getExecutionState());
        assertFalse(isDoInEventThreadCalled);
        assertFalse(isBadListenerMethodCalled);
        assertEquals(testException, task.getExecutionException());
        checkOrderingFailureText();
    }

    private void doTest() {
        final Thread startThread = Thread.currentThread();

        task = new BackgroundTask<String>() {
            public void doInBackground() throws Exception {
                assertNotInThread(startThread, "doInBackgroundNotInStartThread");
                assertNotInEventThread("doInBackground");
                Assert.assertEquals(ExecutionState.STARTED, getExecutionState());
                assertOrdering(4, "doInBackground");
                fireProgress(DO_IN_BACKGROUND_PROGRESS_TEXT);
                testException = new RuntimeException("ErrorInDoInBackgroundExecution");
                throw testException;
            }

            public void doInEventThread() throws Exception {
                isDoInEventThreadCalled = true;
            }
        };

        final SwingCommand<String> dummyCommand = new SwingCommand<String>() {
            protected Task<String> createTask() {
                assertInThread(startThread, "createTask in start thread");
                assertOrdering(1, "createTask");
                return task;
            }

            public String toString() {
                return "testErrorInDoInBackground";
            }
        };

        dummyCommand.addTaskListener(new ThreadCheckingTaskListener() {

            public void doPending(Task commandExecution) {
                Assert.assertEquals(ExecutionState.PENDING, task.getExecutionState());
                assertOrdering(2, "pending");
            }

            public void doStarted(Task commandExecution) {
                Assert.assertEquals(ExecutionState.STARTED, task.getExecutionState());
                assertOrdering(3, "started");
            }

            public void doProgress(Task commandExecution, String progressDescription) {
                assertOrdering(5, DO_IN_BACKGROUND_PROGRESS_TEXT);
            }

            public void doSuccess(Task commandExecution) {
                isBadListenerMethodCalled = true;
            }

            public void doError(Task commandExecution, Throwable error) {
                assertEquals(ExecutionState.ERROR, task.getExecutionState());
                assertOrdering(6, "error");
            }

            public void doFinished(Task commandExecution) {
                assertEquals(ExecutionState.ERROR, task.getExecutionState());
                assertOrdering(7, "finished");
                latch.countDown();
            }
        });

        assertEquals(ExecutionState.NOT_RUN, task.getExecutionState());
        dummyCommand.execute();
    }
}