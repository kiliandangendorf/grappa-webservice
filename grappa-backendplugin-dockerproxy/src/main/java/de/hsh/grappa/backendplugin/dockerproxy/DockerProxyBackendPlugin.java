package de.hsh.grappa.backendplugin.dockerproxy;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.jaxrs.JerseyDockerHttpClient;

import de.hsh.grappa.backendplugin.BackendPlugin;
import de.hsh.grappa.util.DebugUtils;
import proforma.util.ProformaSubmissionSubmissionHandle;
import proforma.util.ProformaSubmissionTaskHandle;
import proforma.util.SubmissionLive;
import proforma.util.div.FilenameUtils;
import proforma.util.div.IOUtils;
import proforma.util.div.Strings;
import proforma.util.div.XmlUtils.MarshalOption;
import proforma.util.resource.MimeType;
import proforma.util.resource.ResponseResource;
import proforma.util.resource.SubmissionResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * This class acts like a grader Backend Plugin to the Grappa
 * web service while setting up a Docker container and passing
 * on the submission to the grappa-grader-backend-starter module.
 *
 * This class delivers the submission resource file in a specific
 * directory for the GraderBackendStarter module to retrieve and
 * and start grading. After the grading process has finished, it
 * retrieves the results (a Proforma response resource in case of
 * success, or a grader error stack trace in case of failure),
 * and returns it back to the Grappa web service.
 */
public class DockerProxyBackendPlugin extends BackendPlugin {
    private static final Logger log = LoggerFactory.getLogger(DockerProxyBackendPlugin.class);

    private static final String GRADER_EXCEPTION_STACKTRACE_FILE_PATH =
        "/var/grb_starter/tmp/grader_exception_stacktrace";

    private String graderId = "N/A";
    private String gradeProcId = "N/A";
    
    private String dockerHost;
    private String dockerContainerImage;
    private boolean isPrivateDockerImage;
    private String dockerUsername;
    private String dockerPasswordPat;
    private String copySubmissionToDirectoryPath;
    private String responseResultDirectoryPath;

    //backend-plugin props
    private Properties graderBpProps;
    
    //TODO: remove
    //grader-backend-starter info
    //private String gbsBackendPluginClassname;
    //private String gbsAdditionalRelativeClassPaths;
    //private String gbsFileExtensions;
    
    //private String logLevel; // logLevel inside the container

    //TODO:  remove
//	private static final String STARTER_CFG_PROPERTIES_FILENAME = "grappa-grader-backend-starter.properties";
//	private static final String STARTER_CFG_DIRECTORY_PATH = "/opt/grader-backend-starter";
	//comment that will be added to generated properties files in container
	private static final String DOCKERPROXY_COMMENT="Generated by "+DockerProxyBackendPlugin.class.getSimpleName()+" acc. to host's /etc/grappa/grappa-config.yaml";

	// absolute path inside the container where the grader backend plugin jar lives:
	private static final String GRADER_HOME_IN_CONTAINER = "/opt/grader";
	
	//TODO: remvove
    // Filenames inside the directory GRADER_HOME_IN_CONTAINER:
//	private static final String GRADER_BP_JAR_FILENAME = "graderBP.jar";
	private static final String GRADER_BP_PROPERTIES_FILENAME = "/opt/grader/graderBP.properties";
	
	private static final String COPY_SUBMISSION_TO_DIRECTORY_PATH="/var/grb_starter/tmp";
	private static final String RESPONSE_RESULT_DIRECTORY_PATH="/var/grb_starter/tmp";
	
    @Override
    public void init(Properties props) throws Exception {
        log.debug("Entering DockerProxyBackendPlugin.init()...");

        //these are only the props for the real grader-backend-plugin within container
        //other prefs for this DockerProxyBackendPlugin need to be set by setContext(), setDockerPrefs(), setBackendStarterPrefs()
        graderBpProps=props;
        
        //set these paths fix, since the BackendStarter relies on them
        copySubmissionToDirectoryPath=COPY_SUBMISSION_TO_DIRECTORY_PATH;
        responseResultDirectoryPath=RESPONSE_RESULT_DIRECTORY_PATH;
    }
    public void setContext(String graderId, String graderProcessId){
    	this.graderId=graderId;
    	this.gradeProcId=graderProcessId;
    }
    public void setDockerPrefs(String host, String imageName, String username, String passwordPat){
    	
    	//TODO: add copy pathes for submission etc.
    	//NOT final anymore
    	//TODO: GRADER_BP_PROPERTIES_FILENAME and PATH, too
    	
    	
        dockerHost=host;
        dockerContainerImage = imageName;

        dockerUsername=username;
        dockerPasswordPat=passwordPat;
        
    	isPrivateDockerImage=(username!=null);
	}
//    public void setBackendStarterPrefs(String backendPluginClassname, String additionalRelativeClassPaths, String fileExtensions){
//    	gbsBackendPluginClassname=backendPluginClassname;
//    	gbsAdditionalRelativeClassPaths=additionalRelativeClassPaths;
//    	gbsFileExtensions=fileExtensions;
//    }

    @Override
    public ResponseResource grade(SubmissionResource submission) throws Exception {
        log.debug("[GraderId: '{}', GradeProcId: '{}']: Entering DockerProxyBackendPlugin.grade()...",
                graderId, gradeProcId);
        
        log.info("[GraderId: '{}', GradeProcId: '{}']: Check for external task/submission - then try to embed task/submission",
                graderId, gradeProcId);

        submission = embedSubmissionAndTaskIfExternal(submission);
        
        log.info("[GraderId: '{}', GradeProcId: '{}']: Setting up docker connection to: {}",
            graderId, gradeProcId, dockerHost);

        
        //TODO: Add AuthConfig here with username and pat, use isPrivateDockerImage to check
        
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(dockerHost)
            .withDockerTlsVerify(false)
            .build();

        try (JerseyDockerHttpClient httpClient = new JerseyDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .build(); DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient)) {

            log.info("[GraderId: '{}', GradeProcId: '{}']: Pinging docker daemon...",
                graderId, gradeProcId);
            dockerClient.pingCmd().exec();
            log.info("[GraderId: '{}', GradeProcId: '{}']: Ping successful.",
                graderId, gradeProcId);

             log.info("[GraderId: '{}', GradeProcId: '{}']: Creating container from image '{}'...",
                graderId, gradeProcId, dockerContainerImage);
             List<String> sysProps = Arrays.asList(
                     "-Dfile.encoding=" + Charset.defaultCharset().name(),
                     "-Duser.country=" + Locale.getDefault().getCountry(),
                     "-Duser.language=" + Locale.getDefault().getLanguage(),
                     "-Duser.timezone=" + getHostTimezone(),
                     "-Dlogging.level=" + logLevel);
             List<String> environment= Arrays.asList(
                     "TZ=" + getHostTimezone(),
                     "SYSPROPS=" + String.join(" ", sysProps));
             log.info("[GraderId: '{}', GradeProcId: '{}']: passing environment '{}'...",
                     graderId, gradeProcId, environment.toString());
             String containerId = DockerController.createContainer(dockerClient, dockerContainerImage, environment);
             log.info("[GraderId: '{}', GradeProcId: '{}']: Container with id '{}' created, env={}",
                graderId, gradeProcId, containerId, environment);

             copySubmissionToContainer(dockerClient, containerId, submission);
             //TODO: remove
             //copyBackendStarterProperties(dockerClient,containerId);
             copyBackendPluginProperties(dockerClient,containerId);

             log.info("[GraderId: '{}', GradeProcId: '{}']: Starting container...",
                graderId, gradeProcId);
             // Starts container and subsequentlly the grading process
             DockerController.startContainer(dockerClient, containerId);

             long exitCode = -1;
            try {
                exitCode = waitForContainerToFinishGrading(dockerClient, containerId);
                log.info("[GraderId: '{}', GradeProcId: '{}']: Container finished with exit code {}",
                    graderId, gradeProcId, exitCode);
            } catch (InterruptedException e) {
                log.info("[GraderId: '{}', GradeProcId: '{}']: Thread interrupted while waiting for the grading result, proceeding to deleting docker " +
                    "container...", graderId, gradeProcId);
                Thread.currentThread().interrupt(); // preserve interrupt flag
            }

            ResponseResource responseResource = null;
            String graderStackTrace = null;
            if (0 != exitCode) {
                log.error("[GraderId: '{}', GradeProcId: '{}']: Grader finished abnormally with exit " +
                    "code {}", graderId, gradeProcId, exitCode);
                log.info("[GraderId: '{}', GradeProcId: '{}']: Fetching grader stack trace file: {}",
                    graderId, gradeProcId, GRADER_EXCEPTION_STACKTRACE_FILE_PATH);
                try (InputStream is = DockerController.fetchFile(dockerClient, containerId,
                    GRADER_EXCEPTION_STACKTRACE_FILE_PATH)) {
                    graderStackTrace = IOUtils.toString(is, "utf8");
                } catch (Exception e) {
                    log.error("[GraderId: '{}', GradeProcId: '{}']: Could not load grader stack trace file '{}'.",
                        graderId, gradeProcId, GRADER_EXCEPTION_STACKTRACE_FILE_PATH);
                    log.error(DebugUtils.getStackTrace(e));
                }
            } else {
                // Thread interruption: Do not expect (nor care about) any result or container log
                // with the running container and grading process about to be stopped and removed
                if (!Thread.currentThread().isInterrupted()) {
                    try {
                        responseResource = fetchProformaResponseFile(dockerClient, containerId);
                    } catch (InterruptedException e) {
                        log.info("[GraderId: '{}', GradeProcId: '{}']: Thread interruption during fetching response result file.",
                            graderId, gradeProcId);
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.error("[GraderId: '{}', GradeProcId: '{}']: Failed to fetch response file from container.",
                            graderId, gradeProcId);
                        log.error(e.getMessage());
                        log.error(DebugUtils.getStackTrace(e));
                    }
                }
            }

            if (!Thread.currentThread().isInterrupted()) {
                log.info("[GraderId: '{}', GradeProcId: '{}']: Fetching container log...",
                    graderId, gradeProcId);
                try {
                    List<String> logs = DockerController.getContainerLog(dockerClient, containerId);
                    // display the logs as a single transactional text block so the
                    // lines don't get mixed up with other concurrent logging events
                    StringBuilder sb = new StringBuilder("[START] ======================================================");
                    sb.append(System.getProperty("line.separator"));
                    //logs.forEach(sb::append);
                    for(String s : logs) {
                        sb.append("\t" + s);
                        sb.append(System.getProperty("line.separator"));
                    }
                    sb.append("[END] ======================================================");
                    log.info("[GraderId: '{}', GradeProcId: '{}']: [CONTAINER LOG]:{}{}",
                        graderId, gradeProcId, System.getProperty("line.separator"), sb.toString());
                } catch (InterruptedException e) {
                    log.info("[GraderId: '{}', GradeProcId: '{}']: Thread interruption during fetching response result file.",
                        graderId, gradeProcId);
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("[GraderId: '{}', GradeProcId: '{}']: Fetching container log failed.",
                        graderId, gradeProcId);
                    log.error(e.getMessage());
                    log.error(DebugUtils.getStackTrace(e));
                }
            }

            try {
                log.debug("[GraderId: '{}', GradeProcId: '{}']: Removing container '{}'...",
                    graderId, gradeProcId, containerId);
                DockerController.removeContainer(dockerClient, containerId);
                log.debug("[GraderId: '{}', GradeProcId: '{}']: Container removed: '{}'",
                    graderId, gradeProcId, containerId);
            } catch (Exception e) {
                log.error("[GraderId: '{}', GradeProcId: '{}']: Failed to remove container '{}'",
                    graderId, gradeProcId, containerId);
                log.error(e.getMessage());
            }

            log.info("[GraderId: '{}', GradeProcId: '{}']: DockerProxyBackendPlugin finished.",
                graderId, gradeProcId);
            if (!Strings.isNullOrEmpty(graderStackTrace)) {
                log.info("[GraderId: '{}', GradeProcId: '{}']: Re-throwing grader exception stack trace.",
                    graderId, gradeProcId);
                throw new GraderException(graderStackTrace);
            }

            // if the grader was interrupted, but shut down gracefully, it should still
            // return a null proforma response, and that's what we will return
            return responseResource;
        }
    }
    
    
    private SubmissionResource embedSubmissionAndTaskIfExternal(SubmissionResource submission) throws Exception {
       	SubmissionLive subLive = new SubmissionLive(submission);
    	ProformaSubmissionTaskHandle th = subLive.getSubmissionTaskHandle(getBoundary());
    	ProformaSubmissionSubmissionHandle sh = subLive.getSubmissionSubmissionHandle(getBoundary());
    	
    	boolean converted = th.convertExternalToEmbeddedTask() | sh.convertExternalToEmbeddedSubmission();
    	
    	if (converted) {
    		subLive.markPojoChanged(MarshalOption.none());
    		return subLive.getResource();
    	}
   		return submission;
    }

    
	private byte[] generateBackendPluginPropertiesByteArray() throws IOException{
		Properties backendPluginProps=graderBpProps;

		ByteArrayOutputStream byteArrayOutputStream=new ByteArrayOutputStream();
		backendPluginProps.store(byteArrayOutputStream,DOCKERPROXY_COMMENT);

		byte[] propsBytes=byteArrayOutputStream.toByteArray();
		return propsBytes;
	}
	private void copyBackendPluginProperties(DockerClient dockerClient, String containerId) throws Exception{
		byte[] bpPropsByteArray=generateBackendPluginPropertiesByteArray();
		String bpPropsFileName=GRADER_BP_PROPERTIES_FILENAME;
		String bpPropsDirectory=GRADER_HOME_IN_CONTAINER;
		log.info("[GraderId: '{}', GradeProcId: '{}']: Copying grader properties file '{}' to docker container.",graderId,gradeProcId,
				FilenameUtils.separatorsToUnix(Paths.get(bpPropsDirectory,bpPropsFileName).toString()));
		DockerController.copyFile(bpPropsByteArray,bpPropsDirectory,bpPropsFileName,dockerClient,containerId,true);
	}
    
//	private byte[] generateBackendStarterPropertiesByteArray() throws IOException{
//		//TODO: check whether jars are in /opt/grader/ or in /opt/grader/<SUBDIR>
//		String containerClassPathes=GRADER_HOME_IN_CONTAINER+"/"+GRADER_BP_JAR_FILENAME;
//		String relativeCPs=gbsAdditionalRelativeClassPaths;
//		if(relativeCPs!=null && !relativeCPs.equals("")){
//			String[] relativeCPsArray=relativeCPs.split(";");
//			for(String relCP:relativeCPsArray){
//				containerClassPathes+=";"+GRADER_HOME_IN_CONTAINER+"/"+relCP;
//			}
//		}
//		
//		String fileExtensions=gbsFileExtensions;
//		//this should not occur, since GraderConfig has default value (but you never know...)
//		if(fileExtensions==null)fileExtensions="*.jar";
//		
//		String className=gbsBackendPluginClassname;
//		
//		//TODO: check whether jars are in /opt/grader/ or in /opt/grader/<SUBDIR>
//		//this file will be generated seperatly
//		String configFile=GRADER_HOME_IN_CONTAINER+"/"+GRADER_BP_PROPERTIES_FILENAME;
//
//		Properties backendStarterProps=new Properties();
//		backendStarterProps.setProperty("grappa.plugin.grader.classpathes",containerClassPathes);
//		backendStarterProps.setProperty("grappa.plugin.grader.fileextensions",fileExtensions);
//		backendStarterProps.setProperty("grappa.plugin.grader.class",className);
//		backendStarterProps.setProperty("grappa.plugin.grader.config",configFile);
//
//		ByteArrayOutputStream byteArrayOutputStream=new ByteArrayOutputStream();
//		backendStarterProps.store(byteArrayOutputStream,DOCKERPROXY_COMMENT);
//
//		byte[] propsBytes=byteArrayOutputStream.toByteArray();
//		return propsBytes;
//	}
//	private void copyBackendStarterProperties(DockerClient dockerClient, String containerId) throws Exception{
//		byte[] gbsPropsByteArray=generateBackendStarterPropertiesByteArray();
//		String gbsPropsFileName=STARTER_CFG_PROPERTIES_FILENAME;
//		String gbsPropsDirectory=STARTER_CFG_DIRECTORY_PATH;
//		log.info("[GraderId: '{}', GradeProcId: '{}']: Copying backend-starter properties file '{}' to docker container.",graderId,gradeProcId,
//				FilenameUtils.separatorsToUnix(Paths.get(gbsPropsDirectory,gbsPropsFileName).toString()));
//		DockerController.copyFile(gbsPropsByteArray,gbsPropsDirectory,gbsPropsFileName,dockerClient,containerId,true);
//	}

	 
    private void copySubmissionToContainer(DockerClient dockerClient, String containerId,
                                           SubmissionResource subm) throws Exception {
        String submDestFileName = subm.getMimeType()
            .equals(MimeType.XML) ? "submission.xml" : "submission.zip";
        log.info("[GraderId: '{}', GradeProcId: '{}']: Copying submission file '{}' to docker container.",
            graderId, gradeProcId, FilenameUtils.separatorsToUnix
                (Paths.get(copySubmissionToDirectoryPath, submDestFileName).toString()));
        DockerController.copyFile(subm.getContent(), copySubmissionToDirectoryPath,
            submDestFileName, dockerClient, containerId, true);
    }

    private long waitForContainerToFinishGrading(DockerClient dockerClient, String containerId)
        throws InterruptedException {
        InspectContainerResponse.ContainerState state = dockerClient.inspectContainerCmd(containerId).exec().getState();
        for (int i = 1; !Thread.currentThread().isInterrupted()
            && (state.getRunning() || state.getPaused()) ; ++i) {
            if (i % 20 == 0) {
                // Don't spam the log with waiting messages
                log.debug("[GraderId: '{}', GradeProcId: '{}']: Waiting for the grading process to finish...",
                    graderId, gradeProcId);
            }
            Thread.sleep(1000);
            state = dockerClient.inspectContainerCmd(containerId).exec().getState();
        }

        return state.getExitCodeLong();
    }

    /**
     *
     * @param dockerClient
     * @param containerId
     * @param respFilePath
     * @return the file stream if found, null otherwise
     * @throws Exception
     */
    private InputStream tryFetchResponseFile(DockerClient dockerClient, String containerId,
                                             String respFilePath) throws Exception {
        log.info("[GraderId: '{}', GradeProcId: '{}']: Fetching response file: {}",
            graderId, gradeProcId, respFilePath);
        InputStream resp = null;
        try {
            resp = DockerController.fetchFile(dockerClient, containerId,
                respFilePath);
        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            log.info("[GraderId: '{}', GradeProcId: '{}']: Response file '{}' does not exist.",
                graderId, gradeProcId, respFilePath);
            return null;
        }
        return resp;
    }

    private ResponseResource fetchProformaResponseFile(DockerClient dockerClient, String containerId) throws Exception {
        // We don't know which response mimetype the grader will supply, so we test for both
        // TODO: Grappa should dynamically supply this backend plugin's properties object
        // with a property indicating which mimetype the grader will likely produce based
        // on what's specified in the submission's result-spec element
        String responseXmlPath = Paths.get(responseResultDirectoryPath, "response.xml").toString();
        responseXmlPath = FilenameUtils.separatorsToUnix(responseXmlPath);
        String responseZipPath = Paths.get(responseResultDirectoryPath, "response.zip").toString();
        responseZipPath = FilenameUtils.separatorsToUnix(responseZipPath);

        ResponseResource responseResource = null;
        InputStream resp = tryFetchResponseFile(dockerClient, containerId, responseZipPath);
        if (null != resp) {
            byte[] respBytes = IOUtils.toByteArray(resp);
            responseResource = new ResponseResource(respBytes, MimeType.ZIP);
        } else {
            resp = tryFetchResponseFile(dockerClient, containerId, responseXmlPath);
            if (null != resp) { // try for the other one
                byte[] respBytes = IOUtils.toByteArray(resp);
                responseResource = new ResponseResource(respBytes, MimeType.XML);
            } else
                throw new FileNotFoundException(String.format("Neither '%s' nor '%s' could be " +
                    "retrieved from the container.", responseZipPath, responseXmlPath));
        }

        return responseResource;
    }

    private static String getHostTimezone() {
        ZonedDateTime date = ZonedDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME;
        String text = date.format(formatter).replaceAll("^.*\\[(.*)\\].*$", "$1");
        return text;
    }
}
