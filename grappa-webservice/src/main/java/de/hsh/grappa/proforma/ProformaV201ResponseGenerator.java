package de.hsh.grappa.proforma;

import de.hsh.grappa.proformaxml.v201.*;
import de.hsh.grappa.utils.XmlUtils;
import org.apache.commons.codec.Charsets;

import java.math.BigDecimal;
import java.nio.charset.Charset;

public class ProformaV201ResponseGenerator {

    private ProformaV201ResponseGenerator() {
    }


    /**
     * @param errorMessage
     * @return
     * @throws Exception
     */
    public static ProformaResponse createInternalErrorResponse(String errorMessage) {
        FeedbackListType feedbackList = new FeedbackListType();
        FeedbackType fb = new FeedbackType();
        fb.setLevel(FeedbackLevelType.ERROR);
        fb.setTitle("Error");
        FeedbackType.Content content = new FeedbackType.Content();
        content.setValue(errorMessage);
        content.setFormat("plaintext");
        fb.setContent(content);
        feedbackList.getTeacherFeedback().add(fb);
        feedbackList.getStudentFeedback().add(fb);

        TestsResponseType testsResponse = new TestsResponseType();

        SeparateTestFeedbackType separateTestFeedback = new SeparateTestFeedbackType();
        separateTestFeedback.setTestsResponse(testsResponse);
        separateTestFeedback.setSubmissionFeedbackList(feedbackList);

        GraderEngineType graderEngine = new GraderEngineType();
        graderEngine.setName("N/A");
        graderEngine.setVersion("N/A");

        ResponseMetaDataType responseMetaData = new ResponseMetaDataType();
        responseMetaData.setGraderEngine(graderEngine);

        ResponseType resp = new ResponseType();
        resp.setSeparateTestFeedback(separateTestFeedback);
        resp.setLang("en");
        resp.setFiles(new ResponseFilesType());
        resp.setResponseMetaData(responseMetaData);

        String responseXml = XmlUtils.marshalToXml(resp, ResponseType.class);
        return new ProformaResponse(responseXml.getBytes(Charsets.UTF_8), MimeType.XML);
    }
}
