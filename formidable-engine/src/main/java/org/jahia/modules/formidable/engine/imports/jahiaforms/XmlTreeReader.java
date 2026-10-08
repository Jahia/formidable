package org.jahia.modules.formidable.engine.imports.jahiaforms;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Streams a Jahia document-view export with StAX and builds {@link XmlNode} trees out of it: either the
 * whole export minus the subtrees of one type (the structure, without the submissions), or each
 * subtree of one type on its own (the submissions, one by one). The parser accepts no DTD and resolves
 * no external entity: the file comes from an upload.
 */
final class XmlTreeReader {

    private XmlTreeReader() {
    }

    /**
     * The whole tree, except the subtrees whose root is of {@code skippedType}, which are not even
     * built: the structure of a Forms export is small once its results are left out.
     */
    static XmlNode readTree(InputStream in, String skippedType) throws XMLStreamException {
        XMLStreamReader reader = newReader(in);
        Deque<XmlNode> stack = new ArrayDeque<>();
        XmlNode root = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                XmlNode node = node(reader, stack.peek());
                if (node.isOfType(skippedType)) {
                    skipSubtree(reader);
                    continue;
                }
                if (stack.isEmpty()) {
                    root = node;
                } else {
                    stack.peek().add(node);
                }
                stack.push(node);
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                stack.pop();
            }
        }
        return root;
    }

    /**
     * Each subtree whose root is of {@code type}, built on its own and handed to {@code sink} as soon as
     * its end element is read, so that no more than one of them is in memory at a time.
     */
    static void readSubtrees(InputStream in, String type, Consumer<XmlNode> sink) throws XMLStreamException {
        XMLStreamReader reader = newReader(in);
        Deque<XmlNode> ancestors = new ArrayDeque<>();
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                XmlNode node = node(reader, ancestors.peek());
                if (node.isOfType(type)) {
                    readInto(reader, node);
                    sink.accept(node);
                } else {
                    ancestors.push(node);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                ancestors.pop();
            }
        }
    }

    /** Reads the children of {@code node} until its end element; the reader stands on its start element. */
    private static void readInto(XMLStreamReader reader, XmlNode node) throws XMLStreamException {
        Deque<XmlNode> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty() && reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                XmlNode child = node(reader, stack.peek());
                stack.peek().add(child);
                stack.push(child);
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                stack.pop();
            }
        }
    }

    private static void skipSubtree(XMLStreamReader reader) throws XMLStreamException {
        int depth = 1;
        while (depth > 0 && reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    private static XmlNode node(XMLStreamReader reader, XmlNode parent) {
        String name = Iso9075.decode(qualified(reader.getPrefix(), reader.getLocalName()));
        Map<String, String> attributes = new LinkedHashMap<>();
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            attributes.put(qualified(reader.getAttributePrefix(i), reader.getAttributeLocalName(i)),
                    reader.getAttributeValue(i));
        }
        String path = parent == null ? name : parent.path() + "/" + name;
        return new XmlNode(name, path, attributes);
    }

    private static String qualified(String prefix, String local) {
        return prefix == null || prefix.isEmpty() ? local : prefix + ":" + local;
    }

    private static XMLStreamReader newReader(InputStream in) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        return factory.createXMLStreamReader(in);
    }
}
