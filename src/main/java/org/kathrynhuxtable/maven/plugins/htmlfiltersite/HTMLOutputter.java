/*
 * Copyright (c) 2009 Kathryn Huxtable
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.jdom.Attribute;
import org.jdom.CDATA;
import org.jdom.Comment;
import org.jdom.Element;
import org.jdom.EntityRef;
import org.jdom.Namespace;
import org.jdom.ProcessingInstruction;
import org.jdom.Text;
import org.jdom.Verifier;
import org.jdom.output.EscapeStrategy;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

/**
 * @author Kathryn Huxtable
 */
public class HTMLOutputter extends XMLOutputter {

    protected Format         nonBreakingFormat  = Format.getRawFormat();
    protected EscapeStrategy htmlEscapeStrategy = new HTMLEscapeStrategy();

    public HTMLOutputter() {
        super();
        currentFormat.setEscapeStrategy(htmlEscapeStrategy);
        nonBreakingFormat.setEscapeStrategy(htmlEscapeStrategy);
    }

    /**
     * This will create an <code>XMLOutputter</code> with the specified format
     * characteristics. Note the format object is cloned internally before use.
     */
    public HTMLOutputter(Format format) {
        super(format);
        currentFormat.setEscapeStrategy(htmlEscapeStrategy);
        nonBreakingFormat.setEscapeStrategy(htmlEscapeStrategy);
    }

    /**
     * {@inheritDoc}
     */
    public void setFormat(Format newFormat) {
        super.setFormat(newFormat);
        currentFormat.setEscapeStrategy(htmlEscapeStrategy);
    }

    /**
     * This will handle printing of a <code>{@link Element}</code>, its
     * <code>{@link Attribute}</code>s, and all contained (child) elements, etc.
     * 
     * @param element
     *            <code>Element</code> to output.
     * @param out
     *            <code>Writer</code> to use.
     * @param level
     *            <code>int</code> level of indention.
     * @param namespaces
     *            <code>List</code> stack of Namespaces in scope.
     */
    protected void printElement(Writer out, Element element, int level, NamespaceStack namespaces) throws IOException {

        List<Attribute> attributes = getElementAttributes(element);
        List<?> content = element.getContent();

        // Check for xml:space and adjust format settings
        String space = null;
        if (attributes != null) {
            space = element.getAttributeValue("space", Namespace.XML_NAMESPACE);
        }

        Format previousFormat = currentFormat;
        if ("default".equals(space)) {
            currentFormat = getFormat();
        } else if ("preserve".equals(space) || isNonBreaking(element)) {
            currentFormat = nonBreakingFormat;
        }

        // Print the beginning of the tag plus attributes and any
        // necessary namespace declarations
        out.write("<");
        printQualifiedName(out, element);

        // Mark our namespace starting point
        int previouslyDeclaredNamespaces = namespaces.size();

        // Print the element's namespace, if appropriate
        printElementNamespace(out, element, namespaces);

        // Print out additional namespace declarations
        printAdditionalNamespaces(out, element, namespaces);

        // Print out attributes
        if (attributes != null) printAttributes(out, attributes, element, namespaces);

        // Depending on the settings (newlines, textNormalize, etc), we may
        // or may not want to print all of the content, so determine the
        // index of the start of the content we're interested
        // in based on the current settings.

        int start = skipLeadingWhite(content, 0);
        int size = content.size();
        if (start >= size) {
            // Case content is empty or all insignificant whitespace
            if (true || currentFormat.getExpandEmptyElements()) {
                out.write("></");
                printQualifiedName(out, element);
                out.write(">");
            } else {
                out.write(" />");
            }
        } else {
            out.write(">");

            // For a special case where the content is only CDATA
            // or Text we don't want to indent after the start or
            // before the end tag.

            if (nextNonText(content, start) < size) {
                // Case Mixed Content - normal indentation
                newline(out);
                printContentRange(out, content, start, size, level + 1, namespaces);
                newline(out);
                indent(out, level);
            } else {
                // Case all CDATA or Text - no indentation
                printTextRange(out, content, start, size);
            }
            out.write("</");
            printQualifiedName(out, element);
            out.write(">");
        }

        // remove declared namespaces from stack
        while (namespaces.size() > previouslyDeclaredNamespaces) {
            namespaces.pop();
        }

        // Restore our format settings
        currentFormat = previousFormat;
    }

    @SuppressWarnings("unchecked")
    private List<Attribute> getElementAttributes(Element element) {
        return (List<Attribute>) element.getAttributes();
    }

    private boolean isNonBreaking(Element element) {
        String eName = element.getName();

        if ("span".equals(eName) || "p".equals(eName) || "li".equals(eName) || "h1".equals(eName) || "h2".equals(eName)
                || "h3".equals(eName) || "caption".equals(eName) || "sup".equals(eName) || "sub".equals(eName)) {
            return true;
        }
        return false;
    }

    /**
     * This will handle printing a string. Escapes the element entities, trims
     * interior whitespace, etc. if necessary.
     */
    private void printString(Writer out, String str) throws IOException {
        if (currentFormat.getTextMode() == Format.TextMode.NORMALIZE) {
            str = Text.normalizeString(str);
        } else if (currentFormat.getTextMode() == Format.TextMode.TRIM) {
            str = str.trim();
        }
        out.write(escapeElementEntities(str));
    }

    /**
     * This will print a newline only if indent is not null.
     * 
     * @param out
     *            <code>Writer</code> to use
     */
    private void newline(Writer out) throws IOException {
        if (currentFormat.getIndent() != null) {
            out.write(currentFormat.getLineSeparator());
        }
    }

    /**
     * This will print indents only if indent is not null or the empty string.
     * 
     * @param out
     *            <code>Writer</code> to use
     * @param level
     *            current indent level
     */
    private void indent(Writer out, int level) throws IOException {
        if (currentFormat.getIndent() == null || currentFormat.getIndent().equals("")) {
            return;
        }

        for (int i = 0; i < level; i++) {
            out.write(currentFormat.getIndent());
        }
    }

    // Returns the index of the first non-all-whitespace CDATA or Text,
    // index = content.size() is returned if content contains
    // all whitespace.
    // @param start index to begin search (inclusive)
    private int skipLeadingWhite(List<?> content, int start) {
        if (start < 0) {
            start = 0;
        }

        int index = start;
        int size = content.size();
        if (currentFormat.getTextMode() == Format.TextMode.TRIM_FULL_WHITE
                || currentFormat.getTextMode() == Format.TextMode.NORMALIZE || currentFormat.getTextMode() == Format.TextMode.TRIM) {
            while (index < size) {
                if (!isAllWhitespace(content.get(index))) {
                    return index;
                }
                index++;
            }
        }
        return index;
    }

    // Return the index + 1 of the last non-all-whitespace CDATA or
    // Text node, index < 0 is returned
    // if content contains all whitespace.
    // @param start index to begin search (exclusive)
    private int skipTrailingWhite(List<?> content, int start) {
        int size = content.size();
        if (start > size) {
            start = size;
        }

        int index = start;
        if (currentFormat.getTextMode() == Format.TextMode.TRIM_FULL_WHITE
                || currentFormat.getTextMode() == Format.TextMode.NORMALIZE || currentFormat.getTextMode() == Format.TextMode.TRIM) {
            while (index >= 0) {
                if (!isAllWhitespace(content.get(index - 1))) break;
                --index;
            }
        }
        return index;
    }

    // Return the next non-CDATA, non-Text, or non-EntityRef node,
    // index = content.size() is returned if there is no more non-CDATA,
    // non-Text, or non-EntiryRef nodes
    // @param start index to begin search (inclusive)
    private static int nextNonText(List<?> content, int start) {
        if (start < 0) {
            start = 0;
        }

        int index = start;
        int size = content.size();
        while (index < size) {
            Object node = content.get(index);
            if (!((node instanceof Text) || (node instanceof EntityRef))) {
                return index;
            }
            index++;
        }
        return size;
    }

    // Determine if a Object is all whitespace
    private boolean isAllWhitespace(Object obj) {
        String str = null;

        if (obj instanceof String) {
            str = (String) obj;
        } else if (obj instanceof Text) {
            str = ((Text) obj).getText();
        } else if (obj instanceof EntityRef) {
            return false;
        } else {
            return false;
        }

        for (int i = 0; i < str.length(); i++) {
            if (!Verifier.isXMLWhitespace(str.charAt(i))) return false;
        }
        return true;
    }

    // Determine if a string starts with a XML whitespace.
    private boolean startsWithWhite(String str) {
        if ((str != null) && (str.length() > 0) && Verifier.isXMLWhitespace(str.charAt(0))) {
            return true;
        }
        return false;
    }

    // Determine if a string ends with a XML whitespace.
    private boolean endsWithWhite(String str) {
        if ((str != null) && (str.length() > 0) && Verifier.isXMLWhitespace(str.charAt(str.length() - 1))) {
            return true;
        }
        return false;
    }

    // Support method to print a name without using elt.getQualifiedName()
    // and thus avoiding a StringBuffer creation and memory churn
    private void printQualifiedName(Writer out, Element e) throws IOException {
        if (e.getNamespace().getPrefix().length() == 0) {
            out.write(e.getName());
        } else {
            out.write(e.getNamespace().getPrefix());
            out.write(':');
            out.write(e.getName());
        }
    }

    /**
     * This will handle printing of content within a given range. The range to
     * print is specified in typical Java fashion; the starting index is
     * inclusive, while the ending index is exclusive.
     * 
     * @param content
     *            <code>List</code> of content to output
     * @param start
     *            index of first content node (inclusive.
     * @param end
     *            index of last content node (exclusive).
     * @param out
     *            <code>Writer</code> to use.
     * @param level
     *            <code>int</code> level of indentation.
     * @param namespaces
     *            <code>List</code> stack of Namespaces in scope.
     */
    private void printContentRange(Writer out, List<?> content, int start, int end, int level, NamespaceStack namespaces)
            throws IOException {
        boolean firstNode; // Flag for 1st node in content
        Object next; // Node we're about to print
        int first, index; // Indexes into the list of content

        index = start;
        while (index < end) {
            firstNode = (index == start) ? true : false;
            next = content.get(index);

            //
            // Handle consecutive CDATA, Text, and EntityRef nodes all at
            // once
            //
            if ((next instanceof Text) || (next instanceof EntityRef)) {
                first = skipLeadingWhite(content, index);
                // Set index to next node for loop
                index = nextNonText(content, first);

                // If it's not all whitespace - print it!
                if (first < index) {
                    if (!firstNode) newline(out);
                    indent(out, level);
                    printTextRange(out, content, first, index);
                }
                continue;
            }

            //
            // Handle other nodes
            //
            if (!firstNode) {
                newline(out);
            }

            indent(out, level);

            if (next instanceof Comment) {
                printComment(out, (Comment) next);
            } else if (next instanceof Element) {
                printElement(out, (Element) next, level, namespaces);
            } else if (next instanceof ProcessingInstruction) {
                printProcessingInstruction(out, (ProcessingInstruction) next);
            } else {
                // XXX if we get here then we have a illegal content, for
                // now we'll just ignore it (probably should throw
                // a exception)
            }

            index++;
        } /* while */
    }

    /**
     * This will handle printing of a sequence of <code>{@link CDATA}</code> or
     * <code>{@link Text}</code> nodes. It is an error to have any other pass
     * this method any other type of node.
     * 
     * @param content
     *            <code>List</code> of content to output
     * @param start
     *            index of first content node (inclusive).
     * @param end
     *            index of last content node (exclusive).
     * @param out
     *            <code>Writer</code> to use.
     */
    private void printTextRange(Writer out, List<?> content, int start, int end) throws IOException {
        String previous; // Previous text printed
        Object node; // Next node to print
        String next; // Next text to print

        previous = null;

        // Remove leading whitespace-only nodes
        start = skipLeadingWhite(content, start);

        int size = content.size();
        if (start < size) {
            // And remove trialing whitespace-only nodes
            end = skipTrailingWhite(content, end);

            for (int i = start; i < end; i++) {
                node = content.get(i);

                // Get the unmangled version of the text
                // we are about to print
                if (node instanceof Text) {
                    next = ((Text) node).getText();
                } else if (node instanceof EntityRef) {
                    next = "&" + ((EntityRef) node).getValue() + ";";
                } else {
                    throw new IllegalStateException("Should see only " + "CDATA, Text, or EntityRef");
                }

                // This may save a little time
                if (next == null || "".equals(next)) {
                    continue;
                }

                // Determine if we need to pad the output (padding is
                // only need in trim or normalizing mode)
                if (previous != null) { // Not 1st node
                    if (currentFormat.getTextMode() == Format.TextMode.NORMALIZE
                            || currentFormat.getTextMode() == Format.TextMode.TRIM) {
                        if ((endsWithWhite(previous)) || (startsWithWhite(next))) {
                            out.write(" ");
                        }
                    }
                }

                // Print the node
                if (node instanceof CDATA) {
                    printCDATA(out, (CDATA) node);
                } else if (node instanceof EntityRef) {
                    printEntityRef(out, (EntityRef) node);
                } else {
                    printString(out, next);
                }

                previous = next;
            }
        }
    }

    /**
     * This will handle printing of any needed <code>{@link Namespace}</code>
     * declarations.
     * 
     * @param ns
     *            <code>Namespace</code> to print definition of
     * @param out
     *            <code>Writer</code> to use.
     */
    private void printNamespace(Writer out, Namespace ns, NamespaceStack namespaces) throws IOException {
        String prefix = ns.getPrefix();
        String uri = ns.getURI();

        // Already printed namespace decl?
        if (uri.equals(namespaces.getURI(prefix))) {
            return;
        }

        out.write(" xmlns");
        if (!prefix.equals("")) {
            out.write(":");
            out.write(prefix);
        }
        out.write("=\"");
        out.write(escapeAttributeEntities(uri));
        out.write("\"");
        namespaces.push(ns);
    }

    private void printElementNamespace(Writer out, Element element, NamespaceStack namespaces) throws IOException {
        // Add namespace decl only if it's not the XML namespace and it's
        // not the NO_NAMESPACE with the prefix "" not yet mapped
        // (we do output xmlns="" if the "" prefix was already used and we
        // need to reclaim it for the NO_NAMESPACE)
        Namespace ns = element.getNamespace();
        if (ns == Namespace.XML_NAMESPACE) {
            return;
        }
        if (!((ns == Namespace.NO_NAMESPACE) && (namespaces.getURI("") == null))) {
            printNamespace(out, ns, namespaces);
        }
    }

    private void printAdditionalNamespaces(Writer out, Element element, NamespaceStack namespaces) throws IOException {
        List<Namespace> list = getElementAdditionalNamespaces(element);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                Namespace additional = list.get(i);
                printNamespace(out, additional, namespaces);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Namespace> getElementAdditionalNamespaces(Element element) {
        return (List<Namespace>) element.getAdditionalNamespaces();
    }

    public class HTMLEscapeStrategy implements EscapeStrategy {
        public boolean shouldEscape(char ch) {
            // Magic numbers for ASCII. If the character isn't
            // printable ASCII, then escape it. Normal XML
            // syntax characters will always be escaped.
            if (ch < ' ' || ch > 127) {
                return true;
            } else {
                return false;
            }
        }
    }
}
