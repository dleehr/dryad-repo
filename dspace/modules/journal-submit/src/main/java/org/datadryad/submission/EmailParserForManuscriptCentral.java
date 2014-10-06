package org.datadryad.submission;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * The Class EmailParserForManuscriptCentral.
 */
public class EmailParserForManuscriptCentral extends EmailParser {
    
    // static block

    /** The Pattern for email field. */
    static Pattern Pattern4EmailField = Pattern.compile("^[^:]+:");
    
    /** The Pattern for nonbreaking space. */
    static Pattern Pattern4NonbreakingSpace = Pattern.compile("\\u00A0");
    
    /** The Pattern for newline chars. */
    static Pattern Pattern4NewlineChars = Pattern.compile("\\n+$");
        
    /** The list of the child tag names under Submission_Metadata tag. */
    static List<String> xmlTagNameMetaSubList;
    
    /** The list of the child tag names under Authors tag. */
    static List<String> xmlTagNameAuthorSubList;
    
    /** The field to XML-tag mapping table. */
    static Map<String, String> fieldToXMLTagTable = 
        new LinkedHashMap<String,String>();
    
    /** The Pattern for sender email address. */
    static Pattern Pattern4SenderEmailAddress =
        Pattern.compile("(\"[^\"]*\"|)\\s*(<|)([^@]+@[^@>]+)(>|)");
    
    /** The pattern for separator lines */
    static Pattern Pattern4separatorLine = Pattern
        .compile("^(-|\\+|\\*|/|=|_){2,}+");

    /** The list of mail fields to be excluded */
    static List<String> tagsTobeExcluded; 
    
    /** The set of mail fields to be excluded */
    static Set<String> tagsTobeExcludedSet; 
    
    static {
    	
        fieldToXMLTagTable.put("journal name","Journal");
        fieldToXMLTagTable.put("journal code","Journal_Code");
	fieldToXMLTagTable.put("print issn","ISSN");
	fieldToXMLTagTable.put("online issn","Online_ISSN");
	fieldToXMLTagTable.put("journal admin email","Journal_Admin_Email");
	fieldToXMLTagTable.put("journal editor","Journal_Editor");
	fieldToXMLTagTable.put("journal editor email", "Journal_Editor_Email");
	fieldToXMLTagTable.put("journal embargo period", "Journal_Embargo_Period");
	fieldToXMLTagTable.put("ms dryad id","Manuscript");
	fieldToXMLTagTable.put("ms reference number","Manuscript");
        fieldToXMLTagTable.put("publication doi","Publication_DOI");
	fieldToXMLTagTable.put("ms title","Article_Title");
	fieldToXMLTagTable.put("ms authors","Authors");
	fieldToXMLTagTable.put("contact author","Corresponding_Author");
	fieldToXMLTagTable.put("contact author email","Email");
	fieldToXMLTagTable.put("contact author address 1","Address_Line_1");
	fieldToXMLTagTable.put("contact author address 2","Address_Line_2");
	fieldToXMLTagTable.put("contact author address 3","Address_Line_3");
	fieldToXMLTagTable.put("contact author city","City");
	fieldToXMLTagTable.put("contact author state","State");
	fieldToXMLTagTable.put("contact author country","Country");
	fieldToXMLTagTable.put("contact author zip/postal code","Zip");
	fieldToXMLTagTable.put("keywords","Classification");
	fieldToXMLTagTable.put("abstract","Abstract");
	fieldToXMLTagTable.put("article status","Article_Status");
	
	// New fields for MolEcol resources GR Note
	fieldToXMLTagTable.put("article type", "Article_Type");
	fieldToXMLTagTable.put("ms citation title", "Citation_Title");
	fieldToXMLTagTable.put("ms citation authors", "Citation_Authors");

        // Accept 'Article type' for PLoS Biology
	fieldToXMLTagTable.put("article type", "Article_Type");

        xmlTagNameAuthorSubList= Arrays.asList(
            "Corresponding_Author",
            "Email",
            "Address_Line_1",
            "Address_Line_2",
            "Address_Line_3",
            "City",
            "State",
            "Country",
            "Zip"
        );
        xmlTagNameMetaSubList= Arrays.asList(
                "Article_Title");
                
        tagsTobeExcluded = Arrays.asList(
            "ms reference number",
            "dryad author url"
        );
        
        tagsTobeExcludedSet = new LinkedHashSet<String>(tagsTobeExcluded);
    }
        
    /** The logger setting. */
    private static Logger LOGGER =
        LoggerFactory.getLogger(EmailParserForManuscriptCentral.class);

    
    
    /**
     * Parses each String stored in a List and returns its results as a
     * ParsingResult object.
     * 
     * @param message the message
     * 
     * @return the parsing result
     * 
     * @see submit.util.EmailParser#parseEmailMessage(java.util.List)
     */
    public ParsingResult parseMessage(List<String>message) {
            
        LOGGER.trace("***** start of parseEmailMessage() *****");

        int lineCounter = 0;
        String fieldName = null;
        String previousField = null;
        
        String StoredLines = "";
        Map<String, String> dataForXml = new LinkedHashMap<String, String>();
        ParsingResult result = new ParsingResult();

        // Scan each line
        parsingBlock:
        for  (String line : message){

            lineCounter++;
            LOGGER.trace(" raw line="+line);
            
            // match field names
            Matcher matcher = Pattern4EmailField.matcher(line);
            // note: url datum ("http:") is not a field token
            if (matcher.find() && !line.startsWith("http") && !"Abstract".equalsIgnoreCase(fieldName)){
                // field candidate is found
                String matchedField = matcher.toMatchResult().group(0);
                
                // remove the separator (":") from this field
                int colonPosition = matchedField.indexOf(":");
                fieldName = matchedField.substring(0, colonPosition).toLowerCase();

                // get the value of this field excluding ":"
                // and removing any nbsp
                String fieldValue;
                if (line.length() > colonPosition+2 && line.codePointAt(colonPosition+2) == 160){  
                    fieldValue = line.substring(colonPosition+3).trim();
                }
                else
                    fieldValue = line.substring(colonPosition+1).trim();
                
                
                // processing block applicable only for the first line of
                // a new e-mail message
                if (fieldName.equals("from")){
                    Matcher me =
                        Pattern4SenderEmailAddress.matcher(fieldValue);
                    if (me.find()){
                        LOGGER.trace("how many groups="+me.groupCount());
                        LOGGER.trace("email address captured:"+me.group(3));
                        result.setSenderEmailAddress(me.group(3));
                    }
                } 
                
                if (!fieldToXMLTagTable.containsKey(fieldName)) {
                    // this field or field-look-like is not saved
                    LOGGER.trace(fieldName + " does not belong to the inclusion-"
                        + "tag-set");
                    if (!tagsTobeExcludedSet.contains(fieldName)) {
                        StoredLines = StoredLines +" "+ line; 
                        LOGGER.trace("new stored line=" + StoredLines);
                        // The field name that was matched will not be used,
                        // reset it to the previous field since we are storing
                        // the entire line
                        fieldName = previousField;
                    } else {
                        LOGGER.trace("\t*** line [" + line + "] is skipped");
                    }
                
                } else {
                    // this field is to be saved

                    // new field is detected; if stored lines exist,
                    // they should be saved now
                    if (!StoredLines.equals("")){
                        // continuous lines were stored for the previous 
                        // field before
                        // save these store lines for the last field
                        if (fieldToXMLTagTable.containsKey(previousField)){
                        	if (previousField.equals("ms authors")) {
                        		String prevName = fieldToXMLTagTable.get(previousField);
                        		
                       			if (dataForXml.containsKey(prevName)) {
                       				// We ignore first setting b/c it's contained in StoredLines
                       				dataForXml.put(prevName, StoredLines);
                        		}
                        		else {
                        			dataForXml.put(prevName, StoredLines);
                        		}
                        	}
                        	else {
                        		dataForXml.put(fieldToXMLTagTable.get(previousField),
                        				StoredLines);
                        	}

                            LOGGER.trace("lastField to be saved="+previousField);
                            LOGGER.trace("its value="+StoredLines);
                        }
                        // clear the line-storage object
                        StoredLines = "";
                    }
                    
                    if (fieldName.equals("abstract")) {
                        LOGGER.trace("reached last parsing field: " + fieldName);
                    }
               
                    LOGGER.trace(fieldToXMLTagTable.get(fieldName)+
                            "="+fieldValue);
                    
                    // if the field is an ID field assign it as the ID for the result object
                    // (the last ID processed will be the ID of the parsed item)
                    if (fieldName.equals("ms dryad id") || fieldName.equals("ms reference number")){
                        Matcher mid = Pattern4MS_Dryad_ID.matcher(fieldValue);
                        if (mid.find()){
                            result.setSubmissionId(mid.group(1));
                            LOGGER.trace("submissionId="+result.getSubmissionId());
                            
                            if (fieldValue.equals(result.getSubmissionId())){
                                LOGGER.trace("value and ID are the same");
                            } else {
                                LOGGER.warn("fieldvalue=["+fieldValue+"]"+
                                    "\tid="+result.getSubmissionId()+" differ");
                                result.setHasFlawedId(true);
                            }
                        }
                    }
                    
                    // save the data of this field
                    Matcher m2 = Pattern4NonbreakingSpace.matcher(fieldValue);
                    if (m2.find()){
                        LOGGER.trace("nbsp was found at:"+lineCounter);
                        fieldValue = fieldValue.replaceAll("\\u00A0", " ");
                        LOGGER.trace("fieldValue="+fieldValue);
                    }
                    // tentatively save the data of this field
                    // more lines may follow ...
                    dataForXml.put(fieldToXMLTagTable.get(fieldName),
                        fieldValue);
                    StoredLines = fieldValue;
                    LOGGER.trace("tentatively saved value so far:" + StoredLines);
                }
            } else {
                // no colon-separated field matched
                // non-1st lines or blank lines
                // append this line to the storage object (StoredLines)
                
                if ((line != null) ){
                    
                    if (!StoredLines.trim().equals("")){
                        Matcher m3 = Pattern4separatorLine.matcher(line);
                        if (m3.find()) {
                            LOGGER.trace("separator line was found; ignore this.");
                            
                            if ((fieldName != null)
                                && (fieldName.equals("abstract"))) {
                                StoredLines = StoredLines + "\n" + line;   //save the line and preserve the original formatting   
                                LOGGER.trace("StoredLines=" + StoredLines);
                                break parsingBlock;
                            }
                        } else {
                        	if (previousField != null && previousField.equals("ms authors")) {
                        		StoredLines = StoredLines + ";" + line;
                        	}
                        	else {
                        		StoredLines = StoredLines + "\n" + line;
                        	}
                            LOGGER.trace("StoredLines=" + StoredLines);
                        }
                    } else {
                    	if (previousField != null && previousField.equals("ms authors")) {
                    		StoredLines += (";" + line);
                    	}
                    	else {
                    		StoredLines +=line;
                    	}
                        LOGGER.trace("StoredLines['' case]=" + StoredLines);
                    }
                } 
            }

            previousField = fieldName;            
        }  // end of for
        
        // Exit-processing: if the last matched field is ABSTRACT, 
        // its data are not saved and they must be saved here
        if (previousField.equals("abstract")){
            dataForXml.put(fieldToXMLTagTable.get("abstract"), 
                StoredLines);
        }
        
        // If Article Type is not present, the default value should be Regular
        if(!dataForXml.containsKey("Article_Type")) {
            dataForXml.put("Article_Type", "Regular");
        }

		LOGGER.trace("***** end of parseEmailMessage() *****");
        result.setSubmissionData(BuildSubmissionDataAsXML(dataForXml));
        return result;
    }
    


    /**
     * Write submission data as xml.
     * 
     * TODO: rewrite this so we're not constructing xml by appending strings!
     * 
     * @param emailData the email data
     * 
     * @return the string builder
     */
    StringBuilder BuildSubmissionDataAsXML(Map<String, String> emailData){
        // this method writes data as XML at once from a String
        
        StringBuilder sb = new StringBuilder();
        Set<Map.Entry<String, String>> keyvalue = emailData.entrySet();
        
        for (Iterator<Map.Entry<String, String>> it = 
                keyvalue.iterator();it.hasNext(); ){
            Map.Entry<String, String> et = it.next();
            
            if (et.getKey().equals("Manuscript")){
                
                sb.append("<Submission_Metadata>\n");
                
                sb.append("\t<"+et.getKey()+">"
                        +getStrippedText(StringUtils.stripToEmpty(
                            et.getValue()))
                        +"</"+et.getKey()+">\n");
                target1:while(true){
                    Map.Entry<String, String> etx = it.next();
                    sb.append("\t<"+etx.getKey()+">"
                        +getStrippedText(StringUtils.stripToEmpty(
                            etx.getValue()))
                        +"</"+etx.getKey()+">\n");
                    if (etx.getKey().equals(
                        xmlTagNameMetaSubList.get(
                            xmlTagNameMetaSubList.size()-1))){
                        sb.append("</Submission_Metadata>\n");
                        break target1;
                    }
                }
            } else if (et.getKey().equals("Classification")) {
                // classification (keywords) are separated by commas or semicolons (commas dominate)
                sb.append("<Classification>\n");
                String[] keywords = processKeywordList(et.getValue());
                for (String kw : keywords){
                    sb.append("\t<keyword>"
                    +getStrippedText(StringUtils.stripToEmpty(kw))
                    +"</keyword>\n");
                }
                sb.append("</Classification>\n");
            } else if (et.getKey().equals("Authors")){
                sb.append("<Authors>\n");
                String[] authors = processAuthorList(et.getValue());
                for (String el : authors){
                    sb.append("\t<Author>"+ el +"</Author>\n");
                }
                sb.append("</Authors>\n");
            } else if (et.getKey().equals("Citation Authors")) {
                sb.append("<Citation_Authors>\n");
                String[] citationAuthors = processAuthorList(et.getValue());
                for (String el : citationAuthors) {
                    sb.append("\t<Citation_Author>" + el + "</Citation_Author>\n");
                }
                sb.append("</Citation_Authors>\n");
            } else if (et.getKey().equals("Contact_Author")){
                sb.append("<Corresponding_Author>\n\t<Name>"+
                        processCorrespondingAuthorName(et.getValue())
			  +"</Name>\n");
                target:while(true){
                    Map.Entry<String, String> etx = it.next();
                    sb.append("\t<"+etx.getKey()+">"
                        +getStrippedText(
                            StringUtils.stripToEmpty(etx.getValue()))
                        +"</"+etx.getKey()+">\n");
                    if (etx.getKey().equals(xmlTagNameAuthorSubList.get(
                            xmlTagNameAuthorSubList.size()-1))){
                        sb.append("</Corresponding_Author>\n");
                        break target;
                    }
                }
            } else {
                
                if (StringUtils.stripToEmpty(et.getValue()).equals("")){
                    sb.append("<"+et.getKey()+" />\n");
                } else {
                	sb.append("<"+et.getKey()+">");
                	sb.append(getStrippedText(et.getValue()));
                    sb.append("</"+et.getKey()+">\n");
                }  
            }
       
        }  // end of for
        
        return sb;
    }
}
