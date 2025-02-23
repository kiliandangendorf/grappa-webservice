package de.hsh.grappa.utils;

import de.hsh.grappa.proforma.MimeType;
import de.hsh.grappa.proforma.ResponseResource;
import de.hsh.grappa.proforma.SubmissionResource;
import proforma.ProformaResponseZipPathes;
import proforma.ProformaSubmissionZipPathes;
import proforma.xml.ResponseType;
import proforma.xml.SubmissionType;

import java.nio.charset.StandardCharsets;

/**
 * Converts a Proforma resource from a ZIP or a
 * bare-bone XML to POJOs using XML binding.
 */
public class ProformaConverter {
    private ProformaConverter() {}

    public static SubmissionType convertToPojo(SubmissionResource submissionResource) throws Exception {
        byte[] submXmlFileBytes = submissionResource.getContent();
        if (submissionResource.getMimeType().equals(MimeType.ZIP)) {
            String submXmlFileContent = Zip.getTextFileContentFromZip(submissionResource.getContent(),
                ProformaSubmissionZipPathes.SUBMISSION_XML_FILE_NAME, StandardCharsets.UTF_8);
            submXmlFileBytes = submXmlFileContent.getBytes(StandardCharsets.UTF_8);
        }
        return XmlUtils.unmarshalToObject(submXmlFileBytes, SubmissionType.class);
    }
    
    public static ResponseType convertToPojo(ResponseResource responseResource) throws Exception {
        byte[] xmlFileBytes = responseResource.getContent();
        if (responseResource.getMimeType().equals(MimeType.ZIP)) {
            String xmlFileContent = de.hsh.grappa.utils.Zip.getTextFileContentFromZip(responseResource.getContent(),
                ProformaResponseZipPathes.RESPONSE_XML_FILE_NAME, StandardCharsets.UTF_8);
            xmlFileBytes = xmlFileContent.getBytes(StandardCharsets.UTF_8);
        }
        return XmlUtils.unmarshalToObject(xmlFileBytes, ResponseType.class);
    }
}
