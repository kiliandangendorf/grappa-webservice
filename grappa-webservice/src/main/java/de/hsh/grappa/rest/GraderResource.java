package de.hsh.grappa.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.JSONPObject;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.hsh.grappa.application.GrappaServlet;
import de.hsh.grappa.config.GraderConfig;
import de.hsh.grappa.exceptions.NotFoundException;
import de.hsh.grappa.service.GraderPoolManager;
import de.hsh.grappa.service.GraderStatistics;
import de.hsh.grappa.utils.Json;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.util.Map;

@Path("/graders/{graderId}")
public class GraderResource {

    Logger log = LoggerFactory.getLogger(GraderResource.class);
    @Context
    ContainerRequestContext sr;
    @Context
    ServletContext sc;

    @Context
    public void setServletContext(ServletContext context) {
        this.sc = context;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getStatus(@PathParam("graderId") String graderId) throws Exception {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return Response.ok().entity(gson.toJson(getGraderStatus(graderId))).build();
    }

    public static JsonObject getGraderStatus(String graderId) throws NotFoundException {
        var gcOpt = GrappaServlet.CONFIG.getGraders().stream()
            .filter(g -> g.getId().equals(graderId)).findFirst();
        if(!gcOpt.isPresent())
            throw new NotFoundException(String.format("GraderId does not exist."));
        var gc = gcOpt.get();

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject graderStatus = new JsonObject();
        graderStatus.addProperty("id", graderId);
        graderStatus.addProperty("name", gc.getName());
        graderStatus.addProperty("poolSize", GraderPoolManager.getInstance().getPoolSize(graderId));
        graderStatus.addProperty("busyCount", GraderPoolManager.getInstance().getBusyCount(graderId));
        graderStatus.addProperty("queuedSubmissionCount",
            GrappaServlet.redis.getSubmissionQueueCount(graderId));
//        graderStatus.addProperty("estimatedGradingSecondsTillQueueProcessed",
//            GraderPoolManager.getInstance().getEstimatedSecondsUntilQueueIsGraded(graderId));
        var gsOpt = GraderPoolManager.getInstance().getGraderStatistics()
            .entrySet().stream().filter(e -> e.getKey().equals(graderId)).findFirst();
        if (gsOpt.isPresent()) {
            var gs = gsOpt.get().getValue();
            graderStatus.addProperty("gradingProcessesExecuted", gs.getExecuted());
            graderStatus.addProperty("gradingProcessesSucceeded", gs.getSucceeded());
            graderStatus.addProperty("gradingProcessesFailed", gs.getFailed());
            graderStatus.addProperty("gradingProcessesCancelled", gs.getCancelled());
            graderStatus.addProperty("gradingProcessesTimedOut", gs.getTimedOut());
        }

        return graderStatus;
    }
}

