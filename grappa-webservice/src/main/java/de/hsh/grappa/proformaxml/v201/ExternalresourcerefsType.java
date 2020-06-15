
package de.hsh.grappa.proformaxml.v201;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;


/**
 * <p>Java-Klasse f�r externalresourcerefs-type complex type.
 * 
 * <p>Das folgende Schemafragment gibt den erwarteten Content an, der in dieser Klasse enthalten ist.
 * 
 * <pre>
 * &lt;complexType name="externalresourcerefs-type">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence maxOccurs="unbounded" minOccurs="0">
 *         &lt;element name="externalresourceref" type="{urn:proforma:v2.0.1}externalresourceref-type"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "externalresourcerefs-type", namespace = "urn:proforma:v2.0.1", propOrder = {
    "externalresourceref"
})
public class ExternalresourcerefsType {

    @XmlElement(namespace = "urn:proforma:v2.0.1")
    protected List<ExternalresourcerefType> externalresourceref;

    /**
     * Gets the value of the externalresourceref property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the externalresourceref property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getExternalresourceref().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ExternalresourcerefType }
     * 
     * 
     */
    public List<ExternalresourcerefType> getExternalresourceref() {
        if (externalresourceref == null) {
            externalresourceref = new ArrayList<ExternalresourcerefType>();
        }
        return this.externalresourceref;
    }

}
