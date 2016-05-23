/*
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package edu.isi.wings.loni2opmw;


import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author Daniel Garijo
 */

/**
 * Class to process and load a LONI pipeline as a Graph
 * The modules are the main processors, and the wasInformedBy dependency is captured
 * amongst them.
 * The LONI templates will be processed from a folder.
 * @author Daniel Garijo
 */
public class LONI2OPMW {
    private HashMap<String,String> inputsOfModules;//for storing the input and the module it belongs to.
    private HashMap<String,String> outputsOfModules;//for storing the output and the module it belongs to.
    private HashMap<String,String> links;//for storing the links among the connections
    private final OntModel opmwRDF;

    
    public LONI2OPMW() {
        //create ontModel here
        opmwRDF = ModelFactory.createOntologyModel();
    }
    
    public void processLONIPipelineFolder(String folderPath){
        //for each of the files, if they are not folders, process them
        //to do
        File f = new File(folderPath);
        if(f.isDirectory()){
            for(File currentTemplate : f.listFiles()){
                this.transformLONITemplateToOPMW(currentTemplate.getPath());
            }
        }else{
            System.out.println("The path introduced as input is not from a folder!");
        }
    }

    private void transformLONITemplateToOPMW(String templatePath) {        
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    System.out.println("Processing "+templatePath);
    try{
        File f = new File(templatePath);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(f);        
        NodeList nodeListConnections = document.getElementsByTagName("connection");
        if(nodeListConnections.getLength()==0){
            System.out.println("The selected workflow has no connections!\n No graph is created");
            return;
        }
        NodeList nodeListModules = document.getElementsByTagName("module");
        NodeList nodeListModuleGroups = document.getElementsByTagName("moduleGroup");
        NodeList dataModuleList = document.getElementsByTagName("dataModule");
        NodeList root = document.getElementsByTagName("pipeline");
        String templateNameWithClass = null, templateName ="", templateLabel="";
        for (int i=0; i< root.getLength(); i++){
            Node template = root.item(i); //there is a template in here
            NodeList templateChildren = template.getChildNodes();
            for(int j =0; j< templateChildren.getLength(); j++){
                Node mainModuleGroup = templateChildren.item(j);
                if("moduleGroup".equals(mainModuleGroup.getNodeName())){
                    NamedNodeMap attr = mainModuleGroup.getAttributes();
                    //this module group has the main metadata for the template. Each with a try.
                    Node currAttr=  attr.getNamedItem("name");
                    if(currAttr!=null){
                        templateLabel = currAttr.getNodeValue();
                    }else{
                        templateLabel = attr.getNamedItem("id").getNodeValue();
                    }
                    templateName = f.getName().replace(".pipe", "");
                    templateNameWithClass = Constants.CONCEPT_WORKFLOW_TEMPLATE+"/"+templateName;
                    GeneralMethods.addIndividual(opmwRDF, templateNameWithClass, Constants.OPMW_WORKFLOW_TEMPLATE, templateLabel);
                    //p-plan
                    GeneralMethods.addSimpleIndividual(opmwRDF, Constants.PREFIX_EXPORT_RESOURCE+GeneralMethods.encode(templateNameWithClass), Constants.P_PLAN_PLAN);
                    GeneralMethods.addDataProperty(opmwRDF, templateNameWithClass, "http://pipeline.loni.usc.edu", Constants.OPMW_DATA_PROP_CREATED_IN_WORKFLOW_SYSTEM);
                    currAttr = attr.getNamedItem("version");
                    if(currAttr!=null){
                        GeneralMethods.addDataProperty(opmwRDF, templateNameWithClass, currAttr.getNodeValue(), Constants.OPMW_DATA_PROP_VERSION_NUMBER);
                    }
                    currAttr = attr.getNamedItem("description");
                    if(currAttr!=null){
                        GeneralMethods.addDataProperty(opmwRDF, templateNameWithClass, currAttr.getNodeValue(), Constants.RDFS_COMMENT);
                        GeneralMethods.addDataProperty(opmwRDF, templateNameWithClass, currAttr.getNodeValue(), Constants.OPMW_DATA_PROP_HAS_DOCUMENTATION);
                    }                   
                    NodeList mainModuleNodes = mainModuleGroup.getChildNodes();
                    for(int k=0; k< mainModuleNodes.getLength(); k++){
                        Node currentChild = mainModuleNodes.item(k);
                        if(null != currentChild.getNodeName())//                        System.out.println(currentChild.getTextContent());
                        switch (currentChild.getNodeName()) {
                            case "authors":
                                NodeList authors = currentChild.getChildNodes();
                                for(int l =0; l<authors.getLength();l++){
                                    NamedNodeMap currAuthorAttrs = authors.item(l).getAttributes();
                                    if(currAuthorAttrs!=null){
                                        Node currAuthor = currAuthorAttrs.getNamedItem("fullName");
                                        if(currAuthor!=null){
                                            GeneralMethods.addDataProperty(opmwRDF, templateNameWithClass, currAuthor.getNodeValue(), Constants.DATA_PROP_CREATOR);
                                        }
                                    }
                                }   break;
                            case "uri":
                                GeneralMethods.addDataProperty(opmwRDF, templateNameWithClass, currentChild.getTextContent(), Constants.OPMW_DATA_PROP_HAS_NATIVE_SYSTEM_TEMPLATE);
                                break;
                            case "metadata":
                                NodeList metadata = currentChild.getChildNodes();
                                for(int l=0; l< metadata.getLength(); l++){
                                    NamedNodeMap currDataAttrs = metadata.item(l).getAttributes();
                                    if(currDataAttrs!=null){
                                        Node currData = currDataAttrs.getNamedItem("key");
                                        if(currData!=null && "__creationDateKey".equals(currData.getNodeValue())){
                                            GeneralMethods.addDataProperty(opmwRDF, templateNameWithClass, currDataAttrs.getNamedItem("value").getNodeValue(), Constants.DATA_PROP_MODIFIED);
                                        }
                                    }
                                }
                                break;
                        }
                    }
                }
            }
        }
        System.out.println("Document with "+nodeListConnections.getLength()+ 
                " connections, "+ nodeListModules.getLength()+" Modules, " +
                nodeListModuleGroups.getLength()+" module groups "+
                dataModuleList.getLength() +" data module groups");
        ArrayList<AbstractMap.SimpleEntry<String,String>> connections = new ArrayList<>(); //for storing the connections: source-sink
        inputsOfModules = new HashMap<>();//for storing the input and the module it belongs to.
        outputsOfModules = new HashMap<>();//for storing the output and the module it belongs to.
        links = new HashMap<>();//for storing the links among the connections
        ArrayList datamodules = new ArrayList<>(); //we need them for later
        //iterate through data modules (input variables of the workflow)
        for(int i=0; i< dataModuleList.getLength(); i++){
            Node current = dataModuleList.item(i);
            String varID = current.getAttributes().getNamedItem("id").getNodeValue();
            String varConceptAndId = Constants.CONCEPT_DATA_VARIABLE+"/"+templateName+"_"+varID;
            Node label = current.getAttributes().getNamedItem("name");
            String nodeLabel = varID;
            if(label!=null){
                nodeLabel = label.getNodeValue();
            }
            //String nodeLabel = current.getAttributes().getNamedItem("name").getNodeValue();
            datamodules.add(varID);
            GeneralMethods.addIndividual(opmwRDF, varConceptAndId, Constants.OPMW_DATA_VARIABLE, nodeLabel);
            GeneralMethods.addProperty(opmwRDF, varConceptAndId, templateNameWithClass, Constants.OPMW_PROP_IS_VARIABLE_OF_TEMPLATE);
            //p-plan
            GeneralMethods.addSimpleIndividual(opmwRDF, Constants.PREFIX_EXPORT_RESOURCE+GeneralMethods.encode(varConceptAndId), Constants.P_PLAN_Variable );
            //retrieve the types of the file
            //I leave it as a to do: I would need to make a namespace available as well.
            //Constants.PREFIX_EXPORT_RESOURCE -> mix of abox and tbox for the moment.
            NodeList currModuleList = current.getChildNodes();
            for(int j=0; j<currModuleList.getLength(); j++){
                Node io = currModuleList.item(j);
                switch (io.getNodeName()) {
                    case "input":
                        inputsOfModules.put(io.getAttributes().getNamedItem("id").getNodeValue(),varID);
//                    System.out.println("Module "+moduleID+" hasInput "+io.getAttributes().getNamedItem("id").getNodeValue());
                        break;
                    case "output":
                        outputsOfModules.put(io.getAttributes().getNamedItem("id").getNodeValue(),varID);
//                    System.out.println("Module "+moduleID+" hasInput "+io.getAttributes().getNamedItem("id").getNodeValue());
                        break;
                }
            }
        }
        //we save the com ponents for doing some quick assertions later.
        for(int i=0; i< nodeListConnections.getLength(); i++){
            Node currentConnection = nodeListConnections.item(i);
            Node source = currentConnection.getAttributes().getNamedItem("source");
            Node sink = currentConnection.getAttributes().getNamedItem("sink");
            //each connection is unique, so it will not be overwritten
            connections.add(new AbstractMap.SimpleEntry(source.getNodeValue(),sink.getNodeValue()));
//            System.out.println(source.getNodeValue()+"-"+sink.getNodeValue());
        }
//        //iterate through modules. Each module id is supposed to be unique.
        for(int i=0; i< nodeListModules.getLength(); i++){
            Node currentModule = nodeListModules.item(i);
            NamedNodeMap nodeAttributes = currentModule.getAttributes();
            String moduleID = nodeAttributes.getNamedItem("id").getNodeValue();
            String moduleIDAndClass = Constants.CONCEPT_WORKFLOW_TEMPLATE_PROCESS+"/"+templateName+"_"+moduleID;
            String moduleType = Constants.PREFIX_EXPORT_RESOURCE+GeneralMethods.clean(nodeAttributes.getNamedItem("name").getNodeValue());
            Node aux = nodeAttributes.getNamedItem("name");
            String name = moduleID;
            if(aux!=null){
                name = aux.getNodeValue();
            }
            aux = nodeAttributes.getNamedItem("description");
            String desc="";
            if(aux!=null){
                desc = aux.getNodeValue();
            }
            GeneralMethods.addIndividual(opmwRDF, moduleIDAndClass, Constants.OPMW_WORKFLOW_TEMPLATE_PROCESS, name);
            if(!"".equals(desc)){
                GeneralMethods.addDataProperty(opmwRDF, moduleIDAndClass, desc, Constants.RDFS_COMMENT);
            }
            GeneralMethods.addProperty(opmwRDF,moduleIDAndClass , templateNameWithClass, Constants.OPMW_PROP_IS_STEP_OF_TEMPLATE);           
            //the module ID is unique in each wf. The module Type could be seen more than once.
            //p-plan
            GeneralMethods.addSimpleIndividual(opmwRDF, Constants.PREFIX_EXPORT_RESOURCE+GeneralMethods.encode(moduleIDAndClass), Constants.P_PLAN_STEP );
            //original type
            GeneralMethods.addSimpleIndividual(opmwRDF, Constants.PREFIX_EXPORT_RESOURCE+GeneralMethods.encode(moduleIDAndClass), moduleType);
            NodeList currModuleList = currentModule.getChildNodes();
            for(int j=0; j<currModuleList.getLength(); j++){
                Node io = currModuleList.item(j);
                switch (io.getNodeName()) {
                    case "input":
                        inputsOfModules.put(io.getAttributes().getNamedItem("id").getNodeValue(),moduleID);
//                    System.out.println("Module "+moduleID+" hasInput "+io.getAttributes().getNamedItem("id").getNodeValue());
                        break;
                    case "output":
                        outputsOfModules.put(io.getAttributes().getNamedItem("id").getNodeValue(),moduleID);
//                    System.out.println("Module "+moduleID+" hasInput "+io.getAttributes().getNamedItem("id").getNodeValue());
                        break;
                }
            }            
        }
        /**
         * In a module group, the inputs and outputs may be connected to other modules.
         * The input of the module group should be associated to the input of the 
         * first module it connects (with the link association)
         */
        for(int i=0;i<nodeListModuleGroups.getLength();i++){
            Node currentModule = nodeListModuleGroups.item(i);
//            the module ID is unique in each wf. The module Type could be seen more than once.
            NodeList currModuleList = currentModule.getChildNodes();
//            System.out.println(currentModule.getAttributes().getNamedItem("id"));
            for(int j=0; j<currModuleList.getLength(); j++){
                Node io = currModuleList.item(j);
                if(io.getNodeName().equals("input")||io.getNodeName().equals("output")){                    
//                    System.out.println(io.getAttributes().getNamedItem("id")+","+io.getAttributes().getNamedItem("link").getNodeValue());
                    links.put(io.getAttributes().getNamedItem("id").getNodeValue(),io.getAttributes().getNamedItem("link").getNodeValue());
                }
            }
        }
        //now that we have the nodes and the URIs, we iterate through the connections
        //in order to determine whether we have a wasInformedBy connection between modules.
        Iterator<AbstractMap.SimpleEntry<String,String>> itConn = connections.iterator();
        while(itConn.hasNext()){
            AbstractMap.SimpleEntry<String,String> currentConnection = itConn.next();
            String source = currentConnection.getKey();
            String sink = currentConnection.getValue();
            String sourceURI, sinkURI;
            //if the source or the sink is in the links matrix, we have to 
            //recover to which module input are they associated.
            if(links.containsKey(source)){
                source = getIOFromModule(links,source);
            }
            if(links.containsKey(sink)){
                sink = getIOFromModule(links,sink);
            }
            if(inputsOfModules.containsKey(source)){
                if(outputsOfModules.containsKey(sink)){
                    sourceURI = Constants.CONCEPT_WORKFLOW_TEMPLATE_PROCESS+"/"+templateName+"_"+inputsOfModules.get(source);
                    sinkURI = Constants.CONCEPT_WORKFLOW_TEMPLATE_PROCESS+"/"+templateName+"_"+outputsOfModules.get(sink);
                    if(datamodules.contains(outputsOfModules.get(sink))){
                            System.out.println(inputsOfModules.get(source)+" used " +outputsOfModules.get(sink));
                            GeneralMethods.addProperty(opmwRDF,sourceURI,Constants.CONCEPT_DATA_VARIABLE+"/"+templateName+"_"+inputsOfModules.get(source) , Constants.OPMW_PROP_USES);
                            //p-plan
                            GeneralMethods.addProperty(opmwRDF,sourceURI,Constants.CONCEPT_DATA_VARIABLE+"/"+templateName+"_"+inputsOfModules.get(source) , Constants.P_PLAN_PROP_HAS_INPUT);
                        }
                        else if(datamodules.contains(inputsOfModules.get(source))){
                            System.out.println(inputsOfModules.get(source)+" wasGeneratedBy " +outputsOfModules.get(sink));
                            GeneralMethods.addProperty(opmwRDF,Constants.CONCEPT_DATA_VARIABLE+"/"+templateName+"_"+inputsOfModules.get(source) , sinkURI, Constants.OPMW_PROP_IGB);           
                            //p-plan
                            GeneralMethods.addProperty(opmwRDF,Constants.CONCEPT_DATA_VARIABLE+"/"+templateName+"_"+inputsOfModules.get(source) , sinkURI, Constants.P_PLAN_PROP_IS_OUTPUT_VAR_OF);           
                        }else{
                            //create intermediate var here  
                            System.out.println(inputsOfModules.get(source)+" Is preceded by" +outputsOfModules.get(sink));
                            String dataVarAux = Constants.CONCEPT_DATA_VARIABLE+"/"+templateName+"_"+source+sink;
                            GeneralMethods.addIndividual(opmwRDF, dataVarAux, Constants.OPMW_DATA_VARIABLE, source+sink);
                            GeneralMethods.addProperty(opmwRDF,sourceURI ,Constants.PREFIX_EXPORT_RESOURCE+GeneralMethods.encode(dataVarAux), Constants.OPMW_PROP_USES);        
                            GeneralMethods.addProperty(opmwRDF,Constants.PREFIX_EXPORT_RESOURCE+GeneralMethods.encode(dataVarAux), sinkURI ,Constants.OPMW_PROP_IGB);  
                            //p-plan
                            GeneralMethods.addSimpleIndividual(opmwRDF, Constants.PREFIX_EXPORT_RESOURCE+GeneralMethods.encode(dataVarAux), Constants.P_PLAN_Variable);
                            GeneralMethods.addProperty(opmwRDF,sourceURI, sinkURI, Constants.P_PLAN_PROP_IS_PRECEDED_BY);           
                        }
                }
            }else {
                if(outputsOfModules.containsKey(source)){
                    if(inputsOfModules.containsKey(sink)){
                        sourceURI = Constants.CONCEPT_WORKFLOW_TEMPLATE_PROCESS+"/"+templateName+"_"+outputsOfModules.get(source);
                        sinkURI = Constants.CONCEPT_WORKFLOW_TEMPLATE_PROCESS+"/"+templateName+"_"+inputsOfModules.get(sink);
                        if(datamodules.contains(outputsOfModules.get(source))){
                            System.out.println(inputsOfModules.get(sink)+" used " +outputsOfModules.get(source));
                            GeneralMethods.addProperty(opmwRDF,sinkURI ,Constants.CONCEPT_DATA_VARIABLE+"/"+templateName+"_"+outputsOfModules.get(source), Constants.OPMW_PROP_USES);
                            //p-plan
                            GeneralMethods.addProperty(opmwRDF,sinkURI ,Constants.CONCEPT_DATA_VARIABLE+"/"+templateName+"_"+outputsOfModules.get(source), Constants.P_PLAN_PROP_HAS_INPUT);
                        }
                        else if(datamodules.contains(inputsOfModules.get(sink))){
                            System.out.println(inputsOfModules.get(sink)+" wasGeneratedBy " +outputsOfModules.get(source));
                            GeneralMethods.addProperty(opmwRDF,Constants.CONCEPT_DATA_VARIABLE+"/"+templateName+"_"+inputsOfModules.get(sink) , sourceURI, Constants.OPMW_PROP_IGB);           
                            //p-plan
                            GeneralMethods.addProperty(opmwRDF,Constants.CONCEPT_DATA_VARIABLE+"/"+templateName+"_"+inputsOfModules.get(sink) , sourceURI, Constants.P_PLAN_PROP_IS_OUTPUT_VAR_OF);           
                        }else{
                            System.out.println(inputsOfModules.get(sink)+" Is preceded by " +outputsOfModules.get(source));
                            String dataVarAux = Constants.CONCEPT_DATA_VARIABLE+"/"+templateName+"_"+sink+source;
                            GeneralMethods.addIndividual(opmwRDF, dataVarAux, Constants.OPMW_DATA_VARIABLE, sink+source);
                            GeneralMethods.addProperty(opmwRDF,sinkURI ,Constants.PREFIX_EXPORT_RESOURCE+GeneralMethods.encode(dataVarAux), Constants.OPMW_PROP_USES);        
                            GeneralMethods.addProperty(opmwRDF,Constants.PREFIX_EXPORT_RESOURCE+GeneralMethods.encode(dataVarAux), sourceURI ,Constants.OPMW_PROP_IGB);  
                            //p-plan
                            GeneralMethods.addSimpleIndividual(opmwRDF, Constants.PREFIX_EXPORT_RESOURCE+GeneralMethods.encode(dataVarAux), Constants.P_PLAN_Variable);
                            GeneralMethods.addProperty(opmwRDF,sinkURI, sourceURI, Constants.P_PLAN_PROP_IS_PRECEDED_BY);           
                        } 
                    }
                }
            }
        }
        //we make it URIs so as to be able to perform the cleaning tasks later.
//        Graph graph = new Graph(uris, nodes, adjacencyMatrix, GeneralConstants.PREFIX_FOR_RDF_GENERATION+GeneralMethods.encode(uri));
        
        
    }catch(ParserConfigurationException | SAXException | IOException e){
        System.err.println("Error while processing file "+ e.getMessage());
    }
    }
    
    //Method that navigates through links until it gets the connection between two modules
    private String getIOFromModule(HashMap<String, String> links, String s) {
        String sAux = s;
        while(links.containsKey(sAux)){
            sAux = links.get(sAux);
        }
        return sAux;
    }
    
    public void repostiroyToRDF(String outFile){
        GeneralMethods.exportRDFFile(outFile, opmwRDF);
    }
    
    public static void main(String[] args) {
        LONI2OPMW test = new LONI2OPMW();
//        test.transformToGraph("DTI_workflow.pipe.xml");
//        test.transformToGraph("DTI_eddy_motion_datawith2repeats.pipe.xml");
//        test.transformToGraph("CorticalSurfaceLabeling.pipe.xml");
//        test.transformLONITemplateToOPMW("C:\\Users\\dgarijo\\Desktop\\LONI Pipeline 2 OPMW Converter\\ANTs.pipe");
//        test.transformLONITemplateToOPMW("C:\\Users\\dgarijo\\Desktop\\LONI Pipeline 2 OPMW Converter\\AutoROIExtraction.pipe");
        test.processLONIPipelineFolder("C:\\Users\\dgarijo\\Documents\\GitHub\\LONI2OPMW\\Corpus\\CorpusToConvert");
        test.repostiroyToRDF("loniExport.ttl");
//        String g = "C:\\Users\\dgarijo\\Desktop\\LONI Pipeline 2 OPMW Converter";
    }



    
}
