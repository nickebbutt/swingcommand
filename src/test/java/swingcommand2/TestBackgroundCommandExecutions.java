/**
 *  This file is part of ObjectDefinitions SwingCommand
 *  Copyright (C) Nick Ebbutt September 2009
 *  Licensed under the Academic Free License version 3.0
 *  http://www.opensource.org/licenses/afl-3.0.php
 *
 *  nick@objectdefinitions.com
 *  http://www.objectdefinitions.com/swingcommand
 */

package swingcommand2;

import javax.swing.*;


/**
 * Created by IntelliJ IDEA.
 * User: nick
 * Date: 17-Aug-2008
 * Time: 18:08:30
 * To change this template use File | Settings | File Templates.
 */
public class TestBackgroundCommandExecutions extends CommandTest {

    private RuntimeException doInBackgroundRuntimeException;
    private boolean isdoInBackgroundCalledInSubThread;
    private boolean isDoneCalledInEventThread;
    private boolean isDoneCalled;

    public void doSetUp() {
        isDoneCalledInEventThread = false;
        isDoneCalled = false;
        isdoInBackgroundCalledInSubThread = false;
        doInBackgroundRuntimeException = null;
    }

    //test the correct threads receive the callbacks
    public void testExecutionCallbacksNormalProcessing() {
        BackgroundTask dummyExecution = new NormalExecution();
        assertEquals(ExecutionState.NOT_RUN, dummyExecution.getState());

        final DummyAsynchronousCommand dummyCommand = new DummyAsynchronousCommand(dummyExecution) {
            public String toString() {
                return "testExecutionCallbacksNormalProcessing";
            }
        };

        invokeAndWaitWithFail(
            new Runnable() {
                public void run() {
                    dummyCommand.execute();
                }
            }
        );
        joinLastExecutorThread();
        assertEquals(ExecutionState.SUCCESS, dummyExecution.getState());
        assertTrue(isdoInBackgroundCalledInSubThread);
        assertTrue(isDoneCalled);
        assertTrue(isDoneCalledInEventThread);
    }

    public void testDoneShouldNotBeCalledIfExceptionThrownInDoInBackground() {
        BackgroundTask dummyExecution = new ErrorInDoInBackgroundExecution();
        assertEquals(ExecutionState.NOT_RUN, dummyExecution.getState());

        final DummyAsynchronousCommand dummyCommand = new DummyAsynchronousCommand(dummyExecution) {
            public String toString() {
                return "testDoneShouldNotBeCalledIfExceptionThrownInDoInBackground";
            }
        };

        invokeAndWaitWithFail(
            new Runnable() {
                public void run() {
                    dummyCommand.execute();
                }
            }
        );
        joinLastExecutorThread();
        assertFalse(isDoneCalled);
        assertEquals(ExecutionState.ERROR, dummyExecution.getState());
        assertEquals(doInBackgroundRuntimeException, dummyExecution.getExecutionException());
    }

    private class NormalExecution extends BackgroundTask {

        public void doInBackground() throws Exception {
            isdoInBackgroundCalledInSubThread = ! SwingUtilities.isEventDispatchThread();
            assertEquals(ExecutionState.STARTED, getState());
        }

        public void doInEventThread() throws Exception {
            isDoneCalled = true;
            isDoneCalledInEventThread = SwingUtilities.isEventDispatchThread();
            assertEquals(ExecutionState.STARTED, getState());
        }
    }

    private class ErrorInDoInBackgroundExecution extends BackgroundTask {

        public void doInBackground() throws Exception {
            assertEquals(ExecutionState.STARTED, getState());
            doInBackgroundRuntimeException = new RuntimeException("ErrorInDoInBackgroundExecution");
            throw doInBackgroundRuntimeException;
        }

        public void doInEventThread() throws Exception {
            isDoneCalled = true;
            fail("Should not get to doInEventThread since doInBackground raised Exception");
        }
    }
}