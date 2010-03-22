/*
 * Copyright (c) 2010 Kathryn Huxtable.
 *
 * This file is part of the Image Generator Maven plugin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * $Id$
 */
package org.kathrynhuxtable.maven.plugins.htmlfiltersite;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.doxia.site.decoration.DecorationModel;
import org.apache.maven.doxia.tools.SiteTool;
import org.apache.maven.doxia.tools.SiteToolException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;

import org.codehaus.plexus.i18n.DefaultI18N;
import org.codehaus.plexus.i18n.I18N;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.PathTool;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import org.jdom.input.SAXBuilder;

import org.jdom.output.Format;

import org.jdom.xpath.XPath;

/**
 * Goal runs Velocity on the files in the specified directory.
 *
 * @description                  Runs Velocity on the files in the specified
 *                               directory.
 * @goal                         merge
 * @phase                        pre-site
 * @requiresDependencyResolution runtime
 */
public class MergeMojo extends AbstractMojo {

    /**
     * Specifies the input encoding.
     *
     * @parameter expression="${encoding}"
     *            default-value="${project.build.sourceEncoding}"
     */
    private String inputEncoding;

    /**
     * Specifies the output encoding.
     *
     * @parameter expression="${outputEncoding}"
     *            default-value="${project.reporting.outputEncoding}"
     */
    private String outputEncoding;

    /**
     * Remote repositories used for the project.
     *
     * @todo      this is used for site descriptor resolution - it should relate
     *            to the actual project but for some reason they are not always
     *            filled in
     * @parameter expression="${project.remoteArtifactRepositories}"
     */
    protected List<?> repositories;

    /**
     * The local repository.
     *
     * @parameter expression="${localRepository}"
     */
    protected ArtifactRepository localRepository;

    /**
     * Match pattern for the files to be processed.
     *
     * @parameter expression="${htmlfiltersite.filePattern}"
     *            default-value="**\/*.html,**\/*.html.vm"
     */
    private String filePattern;

    /**
     * Match pattern for the files to be filtered.
     *
     * @parameter expression="${htmlfiltersite.filterExtension}"
     *            default-value=".html.vm"
     */
    private String filterExtension;

    /**
     * Directory containing the site.xml file and the source for apt, fml and
     * xdoc docs, e.g. ${basedir}/src/site.
     *
     * @parameter expression="${htmlfiltersite.siteDirectory}"
     *            default-value="${basedir}/src/site"
     */
    private File siteDirectory;

    /**
     * Location of the source directory.
     *
     * @parameter expression="${htmlfiltersite.sourceDirectory}"
     *            default-value="${basedir}/src/site/html"
     */
    private File sourceDirectory;

    /**
     * Location of the output directory.
     *
     * @parameter expression="${htmlfiltersite.targetDirectory}"
     *            default-value="${project.build.directory}/generated-site/resources"
     */
    private File targetDirectory;

    /**
     * Velocity template for filtering.
     *
     * @parameter expression="${htmlfiltersite.templateFile}"
     *            default-value="${basedir}/src/site/site.vm"
     */
    private File templateFile;

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /** Plexis internationalization element. */
    private I18N i18n = new DefaultI18N();

    /**
     * The reactor projects.
     *
     * @parameter expression="${reactorProjects}"
     * @required
     * @readonly
     */
    protected List<?> reactorProjects;

    /**
     * DOCUMENT ME!
     *
     * @component
     */
    private SiteTool siteTool;

    /**
     * DOCUMENT ME!
     *
     * @return the project
     */
    public MavenProject getProject() {
        return project;
    }

    /**
     * DOCUMENT ME!
     *
     * @param project the project to set
     */
    public void setProject(MavenProject project) {
        this.project = project;
    }

    /**
     * Gets the input files encoding.
     *
     * @return The input files encoding, never <code>null</code>.
     */
    protected String getInputEncoding() {
        return (inputEncoding == null) ? ReaderFactory.ISO_8859_1 : inputEncoding;
    }

    /**
     * DOCUMENT ME!
     *
     * @param inputEncoding the inputEncoding to set
     */
    public void setInputEncoding(String inputEncoding) {
        this.inputEncoding = inputEncoding;
    }

    /**
     * DOCUMENT ME!
     *
     * @param outputEncoding the outputEncoding to set
     */
    public void setOutputEncoding(String outputEncoding) {
        this.outputEncoding = outputEncoding;
    }

    /**
     * Gets the effective reporting output files encoding.
     *
     * @return The effective reporting output file encoding, never <code>
     *         null</code>.
     */
    protected String getOutputEncoding() {
        return (outputEncoding == null) ? ReaderFactory.UTF_8 : outputEncoding;
    }

    /**
     * DOCUMENT ME!
     *
     * @param repositories the repositories to set
     */
    public void setRepositories(List<?> repositories) {
        this.repositories = repositories;
    }

    /**
     * DOCUMENT ME!
     *
     * @param localRepository the localRepository to set
     */
    public void setLocalRepository(ArtifactRepository localRepository) {
        this.localRepository = localRepository;
    }

    /**
     * DOCUMENT ME!
     *
     * @param filePattern the filePattern to set
     */
    public void setFilePattern(String filePattern) {
        this.filePattern = filePattern;
    }

    /**
     * DOCUMENT ME!
     *
     * @param siteDirectory the siteDirectory to set
     */
    public void setSiteDirectory(File siteDirectory) {
        this.siteDirectory = siteDirectory;
    }

    /**
     * DOCUMENT ME!
     *
     * @param sourceDirectory the sourceDirectory to set
     */
    public void setSourceDirectory(File sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

    /**
     * DOCUMENT ME!
     *
     * @param targetDirectory the targetDirectory to set
     */
    public void setTargetDirectory(File targetDirectory) {
        this.targetDirectory = targetDirectory;
    }

    /**
     * DOCUMENT ME!
     *
     * @param templateFile the templateFile to set
     */
    public void setTemplateFile(File templateFile) {
        this.templateFile = templateFile;
    }

    /**
     * DOCUMENT ME!
     *
     * @param i18n the i18n to set
     */
    public void setI18n(I18N i18n) {
        this.i18n = i18n;
    }

    /**
     * DOCUMENT ME!
     *
     * @param reactorProjects the reactorProjects to set
     */
    public void setReactorProjects(List<?> reactorProjects) {
        this.reactorProjects = reactorProjects;
    }

    /**
     * DOCUMENT ME!
     *
     * @param siteTool the siteTool to set
     */
    public void setSiteTool(SiteTool siteTool) {
        this.siteTool = siteTool;
    }

    /**
     * @see org.apache.maven.plugin.AbstractMojo#execute()
     */
    public void execute() throws MojoExecutionException {
        VelocityEngine ve         = initializeVelocityEngine();
        Template       template   = getVelocityTemplate(ve);
        AttributeMap   attributes = new AttributeMap();

        try {
            ((DefaultI18N) i18n).initialize();
        } catch (InitializationException e) {
            e.printStackTrace();
            throw new MojoExecutionException("Unable to initialize I18N object", e);
        }

        if (attributes.get("project") == null) {
            attributes.put("project", project);
        }

        // Put any of the properties in directly into the Velocity attributes
        attributes.putAll(project.getProperties());

        if (attributes.get("inputEncoding") == null) {
            attributes.put("inputEncoding", getInputEncoding());
        }

        if (attributes.get("outputEncoding") == null) {
            attributes.put("outputEncoding", getOutputEncoding());
        }

        DecorationModel decorationModel;

        try {
            decorationModel = siteTool.getDecorationModel(project, reactorProjects, localRepository, repositories,
                                                          getRelativeFilePath(project.getBasedir(), siteDirectory),
                                                          Locale.getDefault(), getInputEncoding(), getOutputEncoding());
        } catch (SiteToolException e) {
            throw new MojoExecutionException("SiteToolException: " + e.getMessage(), e);
        }

        attributes.put("currentDate", new Date());

        attributes.put("lastPublished", new SimpleDateFormat("dd MMM yyyy").format(new Date()));

        List<String> fileList = getFileList();

        for (String file : fileList) {
            filterFile(file, ve, template, decorationModel, attributes);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @return
     *
     * @throws MojoExecutionException
     */
    private VelocityEngine initializeVelocityEngine() throws MojoExecutionException {
        VelocityEngine ve = new VelocityEngine();

        try {
            ve.init();
        } catch (Exception e) {
            e.printStackTrace();
            throw new MojoExecutionException("Unable to initialize Velocity engine", e);
        }

        return ve;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  ve
     *
     * @return
     *
     * @throws MojoExecutionException
     */
    private Template getVelocityTemplate(VelocityEngine ve) throws MojoExecutionException {
        Template template = null;

        try {
            template = ve.getTemplate(getRelativeFilePath(project.getBasedir(), templateFile));
        } catch (ResourceNotFoundException e) {
            e.printStackTrace();
            throw new MojoExecutionException("Unable to locate template " + templateFile, e);
        } catch (ParseErrorException e) {
            e.printStackTrace();
            throw new MojoExecutionException("Problem parsing the template", e);
        } catch (MethodInvocationException e) {
            e.printStackTrace();
            throw new MojoExecutionException("Something invoked in the template threw an exception", e);
        } catch (Exception e) {
            e.printStackTrace();
            throw new MojoExecutionException("Some random template parsing error occurred", e);
        }

        return template;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  file
     * @param  ve              TODO
     * @param  template        ve
     * @param  decorationModel TODO
     * @param  attributes
     *
     * @throws MojoExecutionException
     */
    private void filterFile(String file, VelocityEngine ve, Template template, DecorationModel decorationModel, AttributeMap attributes)
        throws MojoExecutionException {
        File    sourceFile  = new File(sourceDirectory, file);
        boolean doFiltering = (filterExtension != null && filterExtension.equals(file.substring(file.length() - filterExtension.length())));

        File       targetFile = null;
        FileWriter fileWriter = null;
        Reader     fileReader = null;

        if (doFiltering) {
            targetFile = new File(targetDirectory, file.substring(0, file.length() - filterExtension.length()) + ".html");
        } else {
            targetFile = new File(targetDirectory, file);
        }

        if (!targetFile.getParentFile().exists()) {
            targetFile.getParentFile().mkdirs();
        }

        VelocityContext context = createContext(sourceFile, decorationModel, attributes);

        try {
            StringWriter sw = new StringWriter();

            fileReader = new InputStreamReader(new FileInputStream(sourceFile), "UTF-8");
            // If file ends in filter extension, filter it through Velocity before merging with template.
            if (doFiltering) {
                if (!ve.evaluate(context, sw, "htmlfilter-site", fileReader)) {
                    throw new MojoExecutionException("Unable to evaluate html file " + sourceFile);
                }

                closeReader(fileReader);
                fileReader = new StringReader(sw.toString());
            }

            Document doc = parseXHTMLDocument(fileReader);

            addInfoFromDocument(context, decorationModel, doc, null);

            fileWriter = new FileWriter(targetFile);

            template.merge(context, fileWriter);
        } catch (Exception e) {
            e.printStackTrace();
            throw new MojoExecutionException("Unable to merge Velocity", e);
        } finally {
            closeReader(fileReader);
            closeWriter(fileWriter);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param context         DOCUMENT ME!
     * @param decorationModel DOCUMENT ME!
     * @param doc             DOCUMENT ME!
     * @param createDate      TODO
     */
    private void addInfoFromDocument(VelocityContext context, DecorationModel decorationModel, Document doc, Date createDate) {
        context.put("authors", getAuthors(doc));

        String title = "";

        if (decorationModel.getName() != null) {
            title = decorationModel.getName();
        } else if (project.getName() != null) {
            title = project.getName();
        }

        if (title.length() > 0) {
            title += " - ";
        }

        title += getTitle(doc);

        context.put("title", title);

        context.put("headContent", getHeadContent(doc));

        context.put("bodyContent", getBodyContent(doc));

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");

        if (createDate != null) {
            context.put("dateCreation", sdf.format(createDate));
        }
    }

    /**
     * Close a Reader ignoring any exception.
     *
     * @param reader the reader to close.
     */
    private void closeReader(Reader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
            }
        }
    }

    /**
     * Close a Writer ignoring any exceptions.
     *
     * @param writer the Writer to close.
     */
    private void closeWriter(FileWriter writer) {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
            }
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @return
     */
    @SuppressWarnings("unchecked")
    private List<String> getFileList() {
        List<String> fileList = null;

        try {
            fileList = FileUtils.getFileNames(sourceDirectory, filePattern, "", false, true);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return fileList;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  oldPath DOCUMENT ME!
     * @param  newPath DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    private String getRelativeFilePath(File oldPath, File newPath) {
        List<String> names = new ArrayList<String>();

        oldPath = oldPath.getAbsoluteFile();
        newPath = newPath.getAbsoluteFile();

        while (newPath != null && !newPath.equals(oldPath)) {
            names.add(newPath.getName());
            newPath = newPath.getParentFile();
        }

        if (newPath == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (int i = names.size() - 1; i >= 0; i--) {
            if (result.length() > 0) {
                result.append('/');
            }

            result.append(names.get(i));
        }

        return result.toString();
    }

    /**
     * DOCUMENT ME!
     *
     * @param  sourceFile      DOCUMENT ME!
     * @param  decorationModel TODO
     * @param  attributes      siteRenderingContext DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    private VelocityContext createContext(File sourceFile, DecorationModel decorationModel, AttributeMap attributes) {
        VelocityContext context      = new VelocityContext();

        // ----------------------------------------------------------------------
        // Data objects
        // ----------------------------------------------------------------------

        String          relativePath = PathTool.getRelativePath(sourceDirectory.getPath(), sourceFile.getPath());

        context.put("relativePath", relativePath);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");

        context.put("dateRevision", sdf.format(new Date()));

        context.put("decoration", decorationModel);

        context.put("currentDate", new Date());

        Locale locale = Locale.getDefault();

        context.put("dateFormat", DateFormat.getDateInstance(DateFormat.DEFAULT, locale));

        String currentFileName = sourceFile.getName();

        context.put("currentFileName", currentFileName);

        context.put("alignedFileName", PathTool.calculateLink(getRelativeFilePath(sourceDirectory, sourceFile), relativePath));

        context.put("locale", locale);

        // Add global properties.
        if (attributes != null) {
            for (Object o : attributes.keySet()) {
                context.put((String) o, attributes.get(o));
            }
        }

        // ----------------------------------------------------------------------
        // Tools
        // ----------------------------------------------------------------------

        context.put("PathTool", new PathTool());

        context.put("FileUtils", new FileUtils());

        context.put("StringUtils", new StringUtils());

        context.put("i18n", i18n);

        return context;
    }

    /**
     * Parse a document from text in the reader.
     *
     * @param  reader the Reader from which to parse the document.
     *
     * @return the JDom document parsed from the XHTML file.
     *
     * @throws IOException   if the reader cannot be read.
     * @throws JDOMException if the reader cannot be parsed.
     */
    private Document parseXHTMLDocument(Reader reader) throws JDOMException, IOException {
        Document document = null;

        SAXBuilder builder = new SAXBuilder();

        builder.setEntityResolver(new DTDHandler());
        builder.setIgnoringElementContentWhitespace(true);
        builder.setIgnoringBoundaryWhitespace(true);
        document = builder.build(reader);

        return document;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  document DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    private String getTitle(Document document) {
        Element element = null;

        element = selectSingleNode(document.getRootElement(), "/xhtml:html/xhtml:head/xhtml:title");
        if (element == null) {
            return null;
        }

        return element.getText();
    }

    /**
     * DOCUMENT ME!
     *
     * @param  document DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    private List<String> getAuthors(Document document) {
        List<Element> nl   = getXPathList(document, "/xhtml:html/xhtml:head/xhtml:meta[@class='author']");
        List<String>  list = new ArrayList<String>();

        for (Element elem : nl) {
            String author = selectSingleNode(elem, "xhtml:td[@class='author']").getText();

            list.add(author);
        }

        return list;
    }

    /**
     * Extract a list matching an XPath path. This surreptitiously adds the
     * XHTML namespace.
     *
     * @param  document DOCUMENT ME!
     * @param  path     the path to select.
     *
     * @return the list of elements matching the path.
     */
    @SuppressWarnings("unchecked")
    protected List<Element> getXPathList(Document document, String path) {
        try {
            XPath xpath = XPath.newInstance(path);

            xpath.addNamespace("xhtml", "http://www.w3.org/1999/xhtml");
            List<Element> nl = (List<Element>) xpath.selectNodes(document);

            return nl;
        } catch (JDOMException e) {
            return new ArrayList<Element>();
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param  document DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    private String getBodyContent(Document document) {
        Element element = null;

        element = selectSingleNode(document.getRootElement(), "/xhtml:html/xhtml:body");
        if (element == null) {
            return null;
        }

        return getElementContentsAsText(element);
    }

    /**
     * DOCUMENT ME!
     *
     * @param  document DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    private String getHeadContent(Document document) {
        Element element = null;

        element = selectSingleNode(document.getRootElement(), "/xhtml:html/xhtml:head");
        if (element == null) {
            return null;
        }

        return getElementContentsAsText(element);
    }

    /**
     * DOCUMENT ME!
     *
     * @param  element
     *
     * @return
     */
    private String getElementContentsAsText(Element element) {
        StringBuilder text     = new StringBuilder();
        HTMLOutputter writer   = new HTMLOutputter(Format.getPrettyFormat().setTextMode(Format.TextMode.TRIM_FULL_WHITE)
                                                       .setExpandEmptyElements(true));
        List<Element> children = getChildren(element);

        if (children.size() == 0) {
            return element.getText();
        }

        for (Element child : children) {
            StringWriter sw = new StringWriter();

            try {
                writer.output(child, sw);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            text.append(sw);
        }

        return text.toString();
    }

    /**
     * Wrapper around Element.getChildren() to suppress the type warning.
     *
     * @param  element the element whose children to get.
     *
     * @return a List of Elements representing the children of the specified
     *         element.
     */
    @SuppressWarnings("unchecked")
    private List<Element> getChildren(Element element) {
        return (List<Element>) element.getChildren();
    }

    /**
     * DOCUMENT ME!
     *
     * @param  element DOCUMENT ME!
     * @param  path    DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    private Element selectSingleNode(Element element, String path) {
        try {
            XPath xpath = XPath.newInstance(path);

            xpath.addNamespace("xhtml", "http://www.w3.org/1999/xhtml");
            return (Element) xpath.selectSingleNode(element);
        } catch (JDOMException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Simplify references to the attribute hash map.
     */
    private static class AttributeMap extends HashMap<Object, Object> {
        private static final long serialVersionUID = 1787343499009497124L;
    }
}
