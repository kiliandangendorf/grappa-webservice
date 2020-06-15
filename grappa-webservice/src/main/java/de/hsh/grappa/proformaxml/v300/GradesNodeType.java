
package de.hsh.grappa.proformaxml.v300;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;


/**
 * Inner node of the grading scheme hierarchy. There are only two types of inner
 *         nodes: the "root" node and "combine" nodes.
 *       
 * 
 * <p>Java-Klasse f�r grades-node-type complex type.
 * 
 * <p>Das folgende Schemafragment gibt den erwarteten Content an, der in dieser Klasse enthalten ist.
 * 
 * <pre>
 * &lt;complexType name="grades-node-type">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="title" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="description" type="{urn:proforma:v3.0.0}description-type" minOccurs="0"/>
 *         &lt;element name="internal-description" type="{urn:proforma:v3.0.0}description-type" minOccurs="0"/>
 *         &lt;choice maxOccurs="unbounded" minOccurs="0">
 *           &lt;element name="test-ref" type="{urn:proforma:v3.0.0}grades-test-ref-child-type"/>
 *           &lt;element name="combine-ref" type="{urn:proforma:v3.0.0}grades-combine-ref-child-type"/>
 *         &lt;/choice>
 *       &lt;/sequence>
 *       &lt;attribute name="id" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="function" default="min">
 *         &lt;simpleType>
 *           &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *             &lt;enumeration value="min"/>
 *             &lt;enumeration value="max"/>
 *             &lt;enumeration value="sum"/>
 *           &lt;/restriction>
 *         &lt;/simpleType>
 *       &lt;/attribute>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "grades-node-type", namespace = "urn:proforma:v3.0.0", propOrder = {
    "title",
    "description",
    "internalDescription",
    "testRefOrCombineRef"
})
public class GradesNodeType {

    @XmlElement(namespace = "urn:proforma:v3.0.0")
    protected String title;
    @XmlElement(namespace = "urn:proforma:v3.0.0")
    protected String description;
    @XmlElement(name = "internal-description", namespace = "urn:proforma:v3.0.0")
    protected String internalDescription;
    @XmlElements({
        @XmlElement(name = "test-ref", namespace = "urn:proforma:v3.0.0", type = GradesTestRefChildType.class),
        @XmlElement(name = "combine-ref", namespace = "urn:proforma:v3.0.0", type = GradesCombineRefChildType.class)
    })
    protected List<GradesBaseRefChildType> testRefOrCombineRef;
    @XmlAttribute(name = "id")
    protected String id;
    @XmlAttribute(name = "function")
    protected String function;

    /**
     * Ruft den Wert der title-Eigenschaft ab.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTitle() {
        return title;
    }

    /**
     * Legt den Wert der title-Eigenschaft fest.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTitle(String value) {
        this.title = value;
    }

    /**
     * Ruft den Wert der description-Eigenschaft ab.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDescription() {
        return description;
    }

    /**
     * Legt den Wert der description-Eigenschaft fest.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDescription(String value) {
        this.description = value;
    }

    /**
     * Ruft den Wert der internalDescription-Eigenschaft ab.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getInternalDescription() {
        return internalDescription;
    }

    /**
     * Legt den Wert der internalDescription-Eigenschaft fest.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setInternalDescription(String value) {
        this.internalDescription = value;
    }

    /**
     * Gets the value of the testRefOrCombineRef property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the testRefOrCombineRef property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getTestRefOrCombineRef().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link GradesTestRefChildType }
     * {@link GradesCombineRefChildType }
     * 
     * 
     */
    public List<GradesBaseRefChildType> getTestRefOrCombineRef() {
        if (testRefOrCombineRef == null) {
            testRefOrCombineRef = new ArrayList<GradesBaseRefChildType>();
        }
        return this.testRefOrCombineRef;
    }

    /**
     * Ruft den Wert der id-Eigenschaft ab.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getId() {
        return id;
    }

    /**
     * Legt den Wert der id-Eigenschaft fest.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setId(String value) {
        this.id = value;
    }

    /**
     * Ruft den Wert der function-Eigenschaft ab.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFunction() {
        if (function == null) {
            return "min";
        } else {
            return function;
        }
    }

    /**
     * Legt den Wert der function-Eigenschaft fest.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFunction(String value) {
        this.function = value;
    }

}
