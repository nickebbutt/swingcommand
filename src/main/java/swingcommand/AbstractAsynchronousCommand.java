package swingcommand;

import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.Hashtable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author Nick Ebbutt, Object Definitions Ltd. http://www.objectdefinitions.com
 *
 * An abstract superclass for asynchronous Commands
 *
 * Supports ExecutionObserver instances. Events fired to ExecutionObserver instances are always fired on the swing event thread.
 * So a ExecutionObserver instance can be safely be used to update the UI to represent the progress of the command
 */
public abstract class AbstractAsynchronousCommand<E extends AsynchronousExecution> implements AsynchronousCommand<E> {

    private final ExecutionObserverSupport<E> executionObservingSupport = new ExecutionObserverSupport<E>();
    private Executor executor;

    //use of Hashtable rather than HashMap to ensure synchronized access, default access to facilitate testing
    Map<E, ExecutionManager<E>> executionToExecutorMap = new Hashtable<E, ExecutionManager<E>>();


    public AbstractAsynchronousCommand() {
        this(Executors.newSingleThreadExecutor());
    }

    /**
     * @param executor Executor to run this command
     */
    public AbstractAsynchronousCommand(Executor executor) {
        this.executor = executor;
    }

    /**
     * @param executor, the Executor used to run this command
     */
    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public E execute(ExecutionObserver<? super E>... instanceExecutionObservers) {
        return execute(executor, instanceExecutionObservers);
    }

    public E execute(Executor executor, ExecutionObserver<? super E>... instanceExecutionObservers) {
        E execution = createExecutionInEventThread();
        executeCommand(executor, execution, instanceExecutionObservers); //this will be asynchronous unless we have a synchronous executor or are already in a worker thread
        return execution;
    }

    private void executeCommand(Executor executor, E execution, ExecutionObserver<? super E>... instanceExecutionObservers) {

        //get a snapshot list of the execution observers which will receive the events for this execution
        final List<ExecutionObserver<? super E>> observersForExecution = executionObservingSupport.getExecutionObserverSnapshot();
        observersForExecution.addAll(Arrays.asList(instanceExecutionObservers));

        //create a new execution controller for this execution
        ExecutionManager<E> executionManager = createExecutionController(executor, execution, observersForExecution);
        executionManager.executeCommand();
    }

    //subclasses may override this to provide a custom ExecutionManager
    protected ExecutionManager<E> createExecutionController(Executor executor, E execution, List<ExecutionObserver<? super E>> observersForExecution) {
        return new DefaultExecutionManager<E>(
            executor,
            executionToExecutorMap,
            execution,
            observersForExecution
        );
    }

    /**
     * Create an execution.
     * It is important this is done on the event thread because, while creating
     * the execution, state from the ui models or components likely has to be copied/cloned to use as
     * parameters for the async processing. For safety only the event thread should interact with ui
     * components/models. Cloning state from the ui models ensures the background thread has its own
     * copy during execution, and there are no potential race conditions
     */
    private E createExecutionInEventThread() {

        class CreateExecutionRunnable implements Runnable {

            volatile E execution;

            public E getExecution() {
                return execution;
            }

            public void run() {
                execution = createExecution();
            }
        }

        CreateExecutionRunnable r = new CreateExecutionRunnable();
        Throwable t = ExecutionObserverSupport.executeSynchronouslyOnEventThread(r, false);
        E execution = (E)r.getExecution();  //for some reason some jdk need the cast to E to compile
        if ( t != null ) {
            throw new SwingCommandRuntimeException("Cannot run swingcommand \" + getClass().getName() + \" createExecution() threw an execption");
        } else if ( execution == null ) {
            throw new SwingCommandRuntimeException("Cannot run swingcommand " + getClass().getName() + " createExecution() returned null");
        }
        return execution;
    }

    /**
     * @return an Execution for this asyncronous command
     */
    public abstract E createExecution();


    public void addExecutionObserver(ExecutionObserver<? super E> executionObserver) {
        addExecutionObservers(executionObserver);
    }

    public void addExecutionObservers(ExecutionObserver<? super E>... executionObservers) {
        executionObservingSupport.addExecutionObservers(executionObservers);
    }

    public void removeExecutionObserver(ExecutionObserver<? super E> executionObserver) {
        removeExecutionObservers(executionObserver);
    }

    public void removeExecutionObservers(ExecutionObserver<? super E>... executionObservers) {
        executionObservingSupport.removeExecutionObservers(executionObservers);
    }

    /**
     * Fire command error to ExecutionObserver instances
     * Event will be fired on the Swing event thread
     *
     * This has default visiblity so that DefaultCompositeCommand can use it but subclasses should raise an error by throwing it in doInBackground() or doOnEventThread()
     * Subclasses should usually throw an exception during processing - which will trigger an error to be fired and processing to be aborted
     *
     * @param commandExecution execution for executing command
     * @param t the error which occurred
     * @throws SwingCommandRuntimeException, if the execution was not created by this AbstractAsynchronousCommand, or the execution has already stopped
     */
    void fireError(E commandExecution, Throwable t) {
        ExecutionManager<E> c = executionToExecutorMap.get(commandExecution);
        if ( c != null ) {
            List<ExecutionObserver<? super E>> executionObservers = c.getExecutionObservers();
            ExecutionObserverSupport.fireError(executionObservers, commandExecution, t);
        } else {
            throw new SwingCommandRuntimeException("fireError called for unknown execution " + commandExecution);
        }
    }

    /**
     * Fire step reached to ExecutionObserver instances
     * Event will be fired on the Swing event thread
     *
     * @param commandExecution, execution for which to fire progress
     * @throws SwingCommandRuntimeException, if the execution was not created by this AbstractAsynchronousCommand, or the execution has already stopped
     */
    protected void fireProgress(E commandExecution) {
        ExecutionManager<E> c = executionToExecutorMap.get(commandExecution);
        if ( c != null ) {
            List<ExecutionObserver<? super E>> executionObservers = c.getExecutionObservers();
            ExecutionObserverSupport.fireProgress(executionObservers, commandExecution);
        } else {
            throw new SwingCommandRuntimeException("fireProgress called for unknown execution " + commandExecution);
        }
    }

    /**
     * One ExecutionManager exists per execution
     * It maintains the set of observers for the execution, and contains the logic to run the execution, while notifying ExecutionObserver of the progress
     */
    static interface ExecutionManager<E> {

        List<ExecutionObserver<? super E>> getExecutionObservers();

        void executeCommand();
    }


    static class DefaultExecutionManager<E extends AsynchronousExecution> implements ExecutionManager<E> {

        private final Executor executor;
        private final Map<E, ExecutionManager<E>> executionToExecutorMap;
        private final E commandExecution;
        private final List<ExecutionObserver<? super E>> executionObservers;

        public DefaultExecutionManager(Executor executor, Map<E, ExecutionManager<E>> executionToExecutorMap, E commandExecution, List<ExecutionObserver<? super E>> executionObservers) {
            this.executor = executor;
            this.executionToExecutorMap = executionToExecutorMap;
            this.commandExecution = commandExecution;
            this.executionObservers = executionObservers;
        }

        /**
         * This object is used to synchronize memory for each stage of the command processing,
         * This ensures that any state updated during each stage is flushed to shared heap memory before the next stage executes
         * (Since the next stage will executed in a different thread such state changes would not otherwise be guaranteed to be visible)
         */
        private final Object memorySync = new Object();

        public List<ExecutionObserver<? super E>> getExecutionObservers() {
            return executionObservers;
        }

        public void executeCommand() {

            //register the executor against the execution in the map
            executionToExecutorMap.put(commandExecution, DefaultExecutionManager.this);

            //Call fire pending before spawning a new thread. Provided execute was called on the
            //event thread, no more ui work can possibly get done before fireStarting is called
            //If fireStarting is used, for example, to disable a button, this guarantees that the button will be
            //disabled before the action listener triggering the swingcommand returns.
            //otherwise the user might be able to click the button again before the fireStarting callback
            ExecutionObserverSupport.fireStarting(executionObservers, commandExecution);

            //a runnable to do the async portion of the swingcommand
            Runnable executionRunnable = new Runnable() {
                public void run() {
                    try {
                        doExecuteAsync();
                    } finally {
                        executionToExecutorMap.remove(commandExecution);
                    }
                }
            };
             executor.execute(executionRunnable);
        }

        private void doExecuteAsync() {
            //this try block makes sure we always call end up calling fireDone
            try {
                setExecutionState(ExecutionState.STARTED);
                ExecutionObserverSupport.fireStarted(executionObservers, commandExecution);

                synchronized (memorySync) {
                    //STAGE1  - in the current swingcommand processing thread
                    commandExecution.doInBackground();
                }

                //STAGE2 - this needs to be done on the event thread
                runDone(commandExecution);

                setExecutionState(ExecutionState.SUCCESS);
                ExecutionObserverSupport.fireSuccess(executionObservers, commandExecution);
            } catch (Throwable t ) {
                commandExecution.setExecutionException(t);
                setExecutionState(ExecutionState.ERROR);
                ExecutionObserverSupport.fireError(executionObservers, commandExecution, t);
                t.printStackTrace();
            } finally {
                ExecutionObserverSupport.fireDone(executionObservers, commandExecution);
            }
        }

        private void setExecutionState(final ExecutionState newState) {
            ExecutionObserverSupport.executeSynchronouslyOnEventThread(new Runnable(){
                public void run() {
                    commandExecution.setState(newState);
                }
            }, true);
        }

        private void runDone(final E commandExecution) throws Exception {
            class DoneRunnable implements Runnable {
                volatile Throwable t;
                protected Throwable getError() {
                    return t;
                }

                public void run() {
                    synchronized (memorySync) {  //make sure the event thread sees the latest state
                        try {
                            commandExecution.doInEventThread();
                        }
                        catch (Throwable e) {
                            t = e;
                        }
                    }
                }
            }
            DoneRunnable doAfterExecuteRunnable = new DoneRunnable();
            ExecutionObserverSupport.executeSynchronouslyOnEventThread(doAfterExecuteRunnable, true);
            Throwable t = doAfterExecuteRunnable.getError();
            if (t != null) {
                throw new Exception("Failed while invoking runDone() on " + getClass().getName(), t);
            }
        }

    }
}
