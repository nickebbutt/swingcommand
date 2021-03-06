/*
 * Copyright 2009 Object Definitions Ltd.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package swingcommand;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * Created by IntelliJ IDEA.
 * User: Nick Ebbutt (refactored from EBondMarketService)
 * Date: 23-Apr-2009
 * Time: 10:55:25
 */
public abstract class CompositeCommandTask<P,E> extends BackgroundTask<P,E> {

    protected static final Executor SYNCHRONOUS_EXECUTOR = new Executor() {
        public void execute(Runnable command) {
            command.run();
        }
    };

    protected static final Executor INVOKE_AND_WAIT_EXECUTOR = new IfSubThreadInvokeAndWaitExecutor();
    protected static final SwingCommand.ExecutorFactory COMPOSITE_EXECUTOR_FACTORY = new CompositeExecutorFactory();

    private final List<SwingCommand> childCommands = new ArrayList<SwingCommand>();
    private volatile int currentCommandId, totalCommandsExecuting;
    private TaskListenerProxy taskListenerProxy = new TaskListenerProxy();
    private volatile boolean cancelled;

    public CompositeCommandTask() {
    }

    public CompositeCommandTask(SwingCommand... commands) {
        childCommands.addAll(Arrays.asList(commands));
    }

    public CompositeCommandTask(Collection<SwingCommand> commands) {
        childCommands.addAll(commands);
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        cancelled = true;
        taskListenerProxy.cancelCurrentChild();
    }

    public boolean canCancel() {
        return ! getExecutionState().isFinalState();
    }

    /**
     * Execute the child commands
     * @throws Exception
     */
    public void doInBackground() throws Exception {

        List<SwingCommand> children = getChildCommands();
        currentCommandId = 0;
        totalCommandsExecuting = children.size();
        for (final SwingCommand command : children) {
            currentCommandId++;

            excecuteChildCommand(command);

            if (taskListenerProxy.isErrorOccurred()) {
                throw new CompositeCommandTaskException(taskListenerProxy.getLastCommandError());
            }

            if (taskListenerProxy.isLastCommandCancelled()) {
                cancel();
            }

            if (isCancelled()) {
                break;
            }
        }
        taskListenerProxy = null;
    }

    //subclasses could override this to pass in parameters etc.
    protected void excecuteChildCommand(SwingCommand command) {
        command.execute(COMPOSITE_EXECUTOR_FACTORY, taskListenerProxy);
    }

    public void doInEventThread() throws Exception {
    }

    public void addCommand(SwingCommand command) {
        synchronized (childCommands) {
            childCommands.add(command);
        }
    }

    public void addCommands(SwingCommand... commands) {
        synchronized (childCommands) {
            childCommands.addAll(Arrays.asList(commands));
        }
    }

    public void addCommands(Collection<SwingCommand> commands) {
        synchronized (childCommands) {
            childCommands.addAll(commands);
        }
    }

    public void clearChildCommands() {
        synchronized (childCommands) {
            childCommands.clear();
        }
    }

    public int getTotalCommands() {
        synchronized (childCommands) {
            return childCommands.size();
        }
    }

    public List<SwingCommand> getChildCommands() {
        synchronized (childCommands) {
            return Collections.unmodifiableList(childCommands);
        }
    }

    public Task getCurrentChildTask() {
        return taskListenerProxy.getCurrentChildTask();
    }

    public int getCompletedCommandCount() {
        return currentCommandId;
    }

    protected abstract E getProgress(int currentCommandId, int totalCommands, Task currentChildCommand);

    /**
     * Receives execution observer events from child commands and fires step reached events to
     * this composites observers
     */
    private class TaskListenerProxy extends TaskListenerAdapter {
        private volatile boolean errorOccurred;
        private volatile Task currentChildTask;
        private volatile boolean lastCommandCancelled;
        private volatile Throwable lastCommandError;

        public TaskListenerProxy() {
        }

        @Override
        public void started(Task task) {
            this.currentChildTask = task;
            fireProgress(getProgress(currentCommandId, totalCommandsExecuting, task));
        }

        @Override
        public void finished(Task task) {
            lastCommandCancelled = task.isCancelled();
        }

        @Override
        public void error(Task task, Throwable e) {
            errorOccurred = true;
            lastCommandError = e;
        }

        public boolean isErrorOccurred() {
            return errorOccurred;
        }

        public Task getCurrentChildTask() {
            return currentChildTask;
        }

        public boolean isLastCommandCancelled() {
            return lastCommandCancelled;
        }

        public Throwable getLastCommandError() {
            return lastCommandError;
        }

        public void cancelCurrentChild() {
            Task currentTask = currentChildTask;
            if ( currentTask != null) {
                currentTask.cancel();
            }
        }
    }

    private static class CompositeCommandTaskException extends Exception {

        private CompositeCommandTaskException(Throwable cause) {
            super("Error while executing composite command", cause);
        }
    }

    private static class CompositeExecutorFactory implements SwingCommand.ExecutorFactory {

        public Executor getExecutor(Task e) {
            if (e instanceof BackgroundTask) {
                return SYNCHRONOUS_EXECUTOR;
            } else {
                return INVOKE_AND_WAIT_EXECUTOR;
            }
        }
    }

    /**
     * Created by IntelliJ IDEA.
     * User: Nick Ebbutt (refactored from EBondMarketService)
     * Date: 23-Apr-2009
     * Time: 11:43:44
     */
    static class IfSubThreadInvokeAndWaitExecutor implements Executor {
        public void execute(Runnable command) {
            if (SwingUtilities.isEventDispatchThread()) {
                command.run();
            } else {
                try {
                    SwingUtilities.invokeAndWait(command);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
