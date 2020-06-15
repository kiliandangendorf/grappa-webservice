
package de.hsh.grappa.proformaxml.v300;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;


/**
 * <p>Java-Klasse f�r external-resources-type complex type.
 * 
 * <p>Das folgende Schemafragment gibt den erwarteten Content an, der in dieser Klasse enthalten ist.
 * 
 * <pre>
 * &lt;complexType name="external-resources-type">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence maxOccurs="unbounded" minOccurs="0">
 *         &lt;element name="external-resource" type="{urn:proforma:v3.0.0}external-resource-type"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "external-resources-type", namespace = "urn:proforma:v3.0.0", propOrder = {
    "externalResource"
})
public class ExternalResourcesType {

    @XmlElement(name = "external-resource", namespace = "urn:proforma:v3.0.0")
    protected List<ExternalResourceType> externalResource;

    /**
     * Gets the value of the externalResource property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the externalResource property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getExternalResource().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ExternalResourceType }
     * 
     * 
     */
    public List<ExternalResourceType> getExternalResource() {
        if (externalResource == null) {
            externalResource = new ArrayList<ExternalResourceType>();
        }
        return this.externalResource;
    }

}
