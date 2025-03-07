package de.hsh.grappa.service;

import com.google.common.collect.MinMaxPriorityQueue;
import de.hsh.grappa.application.GrappaServlet;
import de.hsh.grappa.cache.QueuedSubmission;
import de.hsh.grappa.cache.RedisController;
import de.hsh.grappa.config.GraderConfig;
import de.hsh.grappa.exceptions.NoResultGraderExecption;
import de.hsh.grappa.exceptions.NotFoundException;
import de.hsh.grappa.plugin.BackendPlugin;
import de.hsh.grappa.proforma.ProformaResponseGenerator;
import de.hsh.grappa.proforma.ResponseResource;
import de.hsh.grappa.utils.BackendPluginLoadingHelper;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages worker threads for a specific graderId.
 * A grader pool consists of a configured number
 * of grader instances of a specific grader type.
 */
public class GraderPool {
    private static final Logger log = LoggerFactory.getLogger(GraderPool.class);
    private final AtomicLong totalGradingProcessesExecuted =
        new AtomicLong(0);
    private final AtomicLong totalGradingProcessesSucceeded =
        new AtomicLong(0);
    private final AtomicLong totalGradingProcessesFailed =
        new AtomicLong(0);
    private final AtomicLong totalGradingProcessesCancelled =
        new AtomicLong(0);
    private final AtomicLong totalGradingProcessesTimedOut =
        new AtomicLong(0);

    private GraderConfig graderConfig;

    private static final String GRAPPA_CONTEXT_GRADER_ID = "Grappa.Context.GraderId";
    private static final String GRAPPA_CONTEXT_GRADE_PROCESS_ID = "Grappa.Context.GraderProcessId";
    private Properties graderConfigInitProps;

    private ConcurrentHashMap<String /*gradeProcId*/, Future<ResponseResource>> gpMap =
        new ConcurrentHashMap<>();

    private HashMap<String /*taskUuid*/, MinMaxPriorityQueue<Duration> /*seconds*/>
        gradingDurationMap = new HashMap<>();

    private Semaphore semaphore;
    private GraderPoolManager graderWorkersMgr;

    public GraderPool(GraderConfig graderConfig, GraderPoolManager graderManager) throws Exception {
        this.graderConfig = graderConfig;

        if(0 >= graderConfig.getConcurrent_grading_processes())
            throw new IllegalArgumentException(String.format("concurrent_grading_processes must not be less than 1 " +
                "for graderId '%s'.", graderConfig.getConcurrent_grading_processes()));

        loadBackendPlugin(graderConfig);
        log.debug("Using grader '{}' with {} concurrent instances.",
            graderConfig.getId(), graderConfig.getConcurrent_grading_processes());
        this.semaphore = new Semaphore(graderConfig.getConcurrent_grading_processes());
        this.graderWorkersMgr = graderManager;
    }

    /**
     * Checks if a submission is availbale for grading for this grader (pool),
     * and if so, the submission is retrieved and graded.
     * <p>
     * Runs asynchronously.
     *
     * @return True, if a grade process has started. False, if all workers are busy
     */
    public boolean tryGrade() {
        // We need to acquire the semaphore this soon already, since we
        // pop a submission from the queue in the next step and commit
        // ourselves to grading it. Otherwise, we'd have to put the submission
        // back into the queue if no available grader instances are available,
        // and that would make the whole concurrency code a whole lot more complicated.
        if (semaphore.tryAcquire()) {
            log.debug("[Grader: '{}']: semaphore aquired, {} left", graderConfig.getId(), semaphore.availablePermits());
            boolean releaseSemaphore = true; // release in current thread if we can't start a grading process
            try {
                QueuedSubmission queuedSubm = RedisController.getInstance().popSubmission(graderConfig.getId());
                if (null != queuedSubm) {
                    releaseSemaphore = false; // starting another thread that will release the semaphore eventually
                    log.info("[GraderId: '{}', GradeProcessId: '{}']: Starting grading process...",
                        graderConfig.getId(), queuedSubm.getGradeProcId());
                    CompletableFuture.supplyAsync(() -> {
                        return runGradingProcess(queuedSubm);
                    }, getJaxbExecutor()).thenAccept(resp -> {
                        cacheProformaResponseResult(resp, queuedSubm.getGradeProcId());
                    }).thenRun(() -> {
                        // We are done. This grader instance has become free, so check if there's anything
                        // queued without prompting.
                        tryGrade(); // reuse future's async thread
                    });
                } else
                    log.debug("[GraderID: '{}']: This grader's submission queue is empty.", graderConfig.getId());
            } catch (Exception e) {
                log.error(e.getMessage());
            } finally {
                if (releaseSemaphore) {
                    semaphore.release();
                    log.debug("[GraderID: '{}']: Nothing to do here. Semaphore released, {} left", graderConfig.getId(),
                        semaphore.availablePermits());
                }
            }
            return true; // will grade
        }
        return false; // will not grade (all workers are busy)
    }

    /**
     * Sets additional grading context properties for the plugin.
     *
     * Sets the graderId and gradeProcessId, so plugins, such as the docker proxy plugin,
     * can use these ids in the plugin's logging context.
     * @param graderId
     * @param gradeProcId
     */
    private Properties getGraderConfigWithContextIds(String graderId, String gradeProcId) {
        Properties propsWithLoggingContext = new Properties();
        synchronized (graderConfigInitProps) {
            graderConfigInitProps.forEach((key, value) -> {
                String k = (String)key;
                String v = (String)value;
                propsWithLoggingContext.setProperty((String)key, (String)value);
            });
        }
        propsWithLoggingContext.setProperty(GRAPPA_CONTEXT_GRADER_ID, graderId);
        propsWithLoggingContext.setProperty(GRAPPA_CONTEXT_GRADE_PROCESS_ID, gradeProcId);
        return propsWithLoggingContext;
    }

    public ResponseResource runGradingProcess(QueuedSubmission subm) {
        try {
            LocalDateTime beginTime = LocalDateTime.now();
            Properties props = getGraderConfigWithContextIds(graderConfig.getId(), subm.getGradeProcId());
            // Create a fresh backend plugin instance for every grading request
            BackendPlugin bp = BackendPluginLoadingHelper.loadBackendPlugin(graderConfig.getClass_name());
            log.info("[GraderId: '{}', GradeProcessId: '{}']: Initializing BackendPlugin...",
                graderConfig.getId(), subm.getGradeProcId());
            bp.init(props);
            FutureTask<ResponseResource> futureTask = null;
            int timeoutSeconds = graderConfig.getTimeout_seconds();
            try {
                futureTask = new FutureTask<ResponseResource>(() -> {
                    return bp.grade(subm.getSubmission());
                });
                gpMap.put(subm.getGradeProcId(), futureTask);
                new Thread(futureTask).start();
                ResponseResource resp = futureTask.get(timeoutSeconds, TimeUnit.SECONDS);
                log.info("[GraderId: '{}', GradeProcessId: '{}']: Grading process exited.",
                    graderConfig.getId(), subm.getGradeProcId());
                if (null != resp) {
                    totalGradingProcessesSucceeded.incrementAndGet();
                    log.info("[GraderId: '{}', GradeProcessId: '{}']: Grading process finished successfully.",
                        graderConfig.getId(), subm.getGradeProcId());
                    // set the average grading duration only for grading processes that actually produced
                    // a valid proforma response. Anything else (such as errors) will skew the average duration.
                    setAverageGradingDuration(Duration.between(beginTime, LocalDateTime.now()),
                        subm.getGradeProcId());
                    return resp;
                }
                throw new NoResultGraderExecption("Grader did not supply a proforma response.");
            } catch (ExecutionException | NoResultGraderExecption e) { // BackendPlugin threw an exception
                totalGradingProcessesFailed.incrementAndGet();
                String errorMessage = String.format("[GraderId: '%s', GradeProcessId: '%s']: Grading process failed " +
                        "with error: %s", graderConfig.getId(), subm.getGradeProcId(), e.getMessage());
                log.error(errorMessage);
                log.error(ExceptionUtils.getStackTrace(e));
                return ProformaResponseGenerator.createInternalErrorResponse(errorMessage);
            } catch (TimeoutException e) {
                totalGradingProcessesTimedOut.incrementAndGet();
                String errorMessage = String.format("[GraderId: '%s', GradeProcessId: '%s']: Grading process timed " +
                    "out after %d seconds.", graderConfig.getId(), subm.getGradeProcId(), timeoutSeconds);
                log.warn(errorMessage);
                log.info("[GraderId: '{}', GradeProcessId: '{}']: Trying to stop timed out grading process...",
                    graderConfig.getId(), subm.getGradeProcId());
                futureTask.cancel(true);
                // Don't increment totalGradingProcessesCancelled, since the cancellation was due to a timeout,
                // not due to a client's delete request
                log.info("[GraderId: '{}', GradeProcessId: '{}']: Grading process has been cancelled after timing out.",
                    graderConfig.getId(), subm.getGradeProcId());
                // How do we know this timeout was due to the grader and not a forever loop in the student submission?
                return ProformaResponseGenerator.createInternalErrorResponse(errorMessage);
            } catch (CancellationException e) {
                totalGradingProcessesCancelled.incrementAndGet();
                log.info("[GraderId: '{}', GradeProcessId: '{}']: Grading process cancelled.",
                    graderConfig.getId(), subm.getGradeProcId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("[GraderId: '{}', GradeProcessId: '{}']: Parent thread of grade process interrupted. Trying to " +
                    "cancel grading process...", graderConfig.getId(), subm.getGradeProcId());
                futureTask.cancel(true);
                // Treat this as cancellation, a direct result of the interrupt
                totalGradingProcessesCancelled.incrementAndGet();
                log.debug("[GraderId: '{}', GradeProcessId: '{}']: Grading process has been cancelled after parent " +
                        "thread interruption.", graderConfig.getId(), subm.getGradeProcId());
                return ProformaResponseGenerator.createInternalErrorResponse("Grading process was interrupted.");
            } catch (Throwable e) { // catch any other error
                totalGradingProcessesFailed.incrementAndGet();
                String errorMessage = String.format("[GraderId: '%s', GradeProcessId: '%s']: Grading process " +
                    "failed with error: %s", graderConfig.getId(), subm.getGradeProcId(), e.getMessage());
                log.error(errorMessage);
                log.error(ExceptionUtils.getStackTrace(e));
                return ProformaResponseGenerator.createInternalErrorResponse(errorMessage);
            }
        } catch (Throwable e) {
            String errorMessage = String.format("[GraderId: '%s', GradeProcessId: '%s']: Grading process encountered " +
                    "error: %s", graderConfig.getId(), subm.getGradeProcId(), e.getMessage());
            log.error(errorMessage);
            log.error(ExceptionUtils.getStackTrace(e));
            return ProformaResponseGenerator.createInternalErrorResponse(errorMessage);
        } finally {
            totalGradingProcessesExecuted.incrementAndGet(); // finished one way or the other
            semaphore.release();
            gpMap.remove(subm.getGradeProcId());
            log.debug("[GraderId: '{}', GradeProcessId: '{}']: semaphore released, {} left", graderConfig.getId(),
                subm.getGradeProcId(), semaphore.availablePermits());
        }
        return null;
    }

    private void setAverageGradingDuration(Duration d, String gradeProcId) {
        try {
            String taskUuid = RedisController.getInstance().getAssociatedTaskUuid(gradeProcId);
            if(null == taskUuid)
                throw new NotFoundException(String
                    .format("There is no associated taskUuid for gradeProcId '{}'.",
                    gradeProcId));

            long avgDuration;
            synchronized (gradingDurationMap) {
                var queue = gradingDurationMap.get(taskUuid);
                if (null == queue) {
                    queue = MinMaxPriorityQueue.maximumSize
                        (GrappaServlet.CONFIG.getService()
                            .getPrev_grading_seconds_max_list_size()).create();
                    gradingDurationMap.put(taskUuid, queue);
                }
                queue.add(d);
                avgDuration = queue.stream().reduce((a, b) -> a.plus(b)).get().toSeconds() / queue.size();
            }

            log.debug("[GraderId: '{}', GradeProcessId: '{}', Task-UUID: '{}']: Average grading duration: {} seconds",
                graderConfig.getId(), gradeProcId, taskUuid, avgDuration);
            RedisController.getInstance().setTaskAverageGradingDurationSeconds(taskUuid, avgDuration);
        } catch (Exception e) {
            log.error("Failed to set grading duration.");
            log.error(e.getMessage());
            log.error(ExceptionUtils.getStackTrace(e));
        }
    }

    private void cacheProformaResponseResult(ResponseResource resp, String gradeProcId) {
        if (null != resp) {
            log.debug("[GraderId: '{}', GradeProcessId: '{}']: Caching response: {}", graderConfig.getId(),
                gradeProcId, resp);
            RedisController.getInstance().setResponse(gradeProcId, resp);
        } else {
            log.debug("[GraderId: '{}', GradeProcessId: '{}']: Grading process did not supply a response result. " +
                "Nothing to cache.", graderConfig.getId(), gradeProcId);
        }
    }

    public boolean cancelGradeProcess(String gradeProcId) {
        var process = gpMap.get(gradeProcId);
        if (null != process) {
            log.debug("GraderPool.cancel(): Trying to cancel grading process with gradeProcId '{}'...",
                gradeProcId);
            process.cancel(true);
            log.debug("GraderPool.cancel(): Grading process with gradeProcId '{}' cancelled: {}",
                gradeProcId, process.isDone());
            return true;
        }
        log.debug("GraderPool.cancel(): No grading process active with gradeProcId '{}'", gradeProcId);
        return false;
    }

    public int getPoolSize() {
        return graderConfig.getConcurrent_grading_processes();
    }

    public int getBusyCount() {
        return getPoolSize() - semaphore.availablePermits();
    }

    public GraderStatistics getGraderStatistics() {
        return new GraderStatistics(
            totalGradingProcessesExecuted.get(),
            totalGradingProcessesSucceeded.get(),
            totalGradingProcessesFailed.get(),
            totalGradingProcessesTimedOut.get(),
            totalGradingProcessesCancelled.get()
        );
    }

    public boolean isGradeProcIdBeingGradedRightNow(String gradeProcId) {
        return gpMap.containsKey(gradeProcId);
    }

    private ResponseResource createInternalErrorResponse(String message) {
        return ProformaResponseGenerator.createInternalErrorResponse(message);
    }

    private void loadBackendPlugin(GraderConfig grader) throws Exception {
        log.info("Loading grader plugin '{}' with classpathes '{}'...", grader.getId(), grader.getClass_path());
        BackendPluginLoadingHelper.loadClasspathLibs(grader.getClass_path(), grader.getFile_extension());
        log.info("Loading grader config file '{}'...", grader.getConfig_path());
        graderConfigInitProps = new Properties();
        try (InputStream is = new FileInputStream(new File(grader.getConfig_path()))) {
            graderConfigInitProps.load(is);
        }
    }

    public long getTotalGradingProcessesExecuted() {
        return totalGradingProcessesExecuted.get();
    }

    public long getTotalGradingProcessesSucceeded() {
        return totalGradingProcessesSucceeded.get();
    }

    public long getTotalGradingProcessesFailed() {
        return totalGradingProcessesFailed.get();
    }

    public long getTotalGradingProcessesCancelled() {
        return totalGradingProcessesCancelled.get();
    }

    public long getTotalGradingProcessesTimedOut() {
        return totalGradingProcessesTimedOut.get();
    }

    private ForkJoinPool getJaxbExecutor() {
        JaxbForkJoinWorkerThreadFactory threadFactory = new JaxbForkJoinWorkerThreadFactory();
        int parallelism = Math.min(0x7fff /* copied from ForkJoinPool.java */, Runtime.getRuntime().availableProcessors());
        return new ForkJoinPool(parallelism, threadFactory, null, false);
    }
}